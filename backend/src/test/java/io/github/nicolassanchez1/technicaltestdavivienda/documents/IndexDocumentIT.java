package io.github.nicolassanchez1.technicaltestdavivienda.documents;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentRepository;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.FileStorage;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.IndexDocument;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure.DocumentsMessagingConfiguration;
import io.github.nicolassanchez1.technicaltestdavivienda.support.AbstractIntegrationTest;
import io.github.nicolassanchez1.technicaltestdavivienda.support.DocumentFixtures;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Indexing against the real schema, because the part that matters cannot be mocked: the stored
 * {@code tsvector} has to be built by the same text search configuration the queries are parsed
 * with, or an accent-insensitive search silently stops matching.
 */
class IndexDocumentIT extends AbstractIntegrationTest {

    private static final long RECEIVE_TIMEOUT_MS = 5_000;
    private static final long SILENCE_TIMEOUT_MS = 300;

    /**
     * A second subscriber on the fan-out, standing in for an api instance. The queue an instance
     * really binds is auto-deleted as soon as a consumer leaves it, which a test that reads with a
     * timeout does on its very first call, so this one is durable and stays put instead.
     */
    private static final String SUBSCRIBER_QUEUE = "documents.status.test-subscriber";

    /** Written with the accents a Spanish document really carries, and searched for without them. */
    private static final String MARKDOWN =
            """
            # Especificación técnica del buscador

            La aplicación indexa documentos técnicos y expone una búsqueda por palabras clave.

            ## Índice invertido

            El índice se reconstruye después de cada modificación.
            """;

    @Autowired
    private IndexDocument indexDocument;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private FileStorage storage;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    @Qualifier("documentStatusQueue") private Queue apiSubscriptionQueue;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM documents").update();
        subscribeToStatusEvents();
        drainStatusQueue();
    }

    /** Also forces the topology to be declared before the first event is published. */
    private void subscribeToStatusEvents() {
        amqpAdmin.declareQueue(new Queue(SUBSCRIBER_QUEUE, true, false, false));
        FanoutExchange statusExchange =
                new FanoutExchange(DocumentsMessagingConfiguration.STATUS_EXCHANGE, true, false);
        amqpAdmin.declareBinding(
                BindingBuilder.bind(new Queue(SUBSCRIBER_QUEUE)).to(statusExchange));
    }

    private Document givenStoredMarkdown(String title) {
        String storageKey = storage.store(new ByteArrayInputStream(MARKDOWN.getBytes(StandardCharsets.UTF_8)), "md");
        return documents.save(DocumentFixtures.processing(title, storageKey));
    }

    private int chunksMatching(UUID documentId, String query) {
        return jdbcClient
                .sql(
                        """
                        SELECT count(*) FROM document_chunks
                        WHERE document_id = :documentId
                          AND search_vector @@ websearch_to_tsquery('es_unaccent', :query)
                        """)
                .param("documentId", documentId)
                .param("query", query)
                .query(Integer.class)
                .single();
    }

    private List<String> headingsOf(UUID documentId) {
        return jdbcClient
                .sql("SELECT heading FROM document_chunks WHERE document_id = :documentId ORDER BY chunk_index")
                .param("documentId", documentId)
                .query(String.class)
                .list();
    }

    private int chunkCountOf(UUID documentId) {
        return jdbcClient
                .sql("SELECT count(*) FROM document_chunks WHERE document_id = :documentId")
                .param("documentId", documentId)
                .query(Integer.class)
                .single();
    }

    @Test
    void movesADocumentToIndexedWithChunksAnAccentInsensitiveSearchCanFind() {
        Document document = givenStoredMarkdown("Guía Davivienda");

        indexDocument.index(document.id());

        Document indexed = documents.findById(document.id()).orElseThrow();
        assertThat(indexed.status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(indexed.chunkCount()).isEqualTo(2);
        assertThat(indexed.indexedAt()).isNotNull();
        assertThat(indexed.processingMs()).isNotNull();
        assertThat(indexed.errorCode()).isNull();
        assertThat(headingsOf(document.id()))
                .containsExactly("Especificación técnica del buscador", "Índice invertido");

        // The premise of the whole design: the query carries no accents and the document does.
        assertThat(chunksMatching(document.id(), "especificacion")).isPositive();
        // Weight A is the title, which appears in no chunk of the body.
        assertThat(chunksMatching(document.id(), "davivienda")).isEqualTo(2);
        // Weight B is the metadata: this one is a tag.
        assertThat(chunksMatching(document.id(), "postgresql")).isEqualTo(2);
        // Weight C is the chunk's own text, so a word of one section matches that section only.
        assertThat(chunksMatching(document.id(), "invertido")).isEqualTo(1);
        assertThat(chunksMatching(document.id(), "inexistente")).isZero();
    }

    @Test
    void replacesTheChunksOfAPreviousRunInsteadOfAddingToThem() {
        Document document = givenStoredMarkdown("Guía Davivienda");
        indexDocument.index(document.id());

        // A finished document is left alone, so the only way to reach a second run is a row that is
        // processing again, which is what the reconciler republishes a job for.
        jdbcClient
                .sql("UPDATE documents SET status = 'PROCESANDO', indexed_at = NULL WHERE id = :id")
                .param("id", document.id())
                .update();

        indexDocument.index(document.id());

        assertThat(chunkCountOf(document.id())).isEqualTo(2);
        assertThat(documents.findById(document.id()).orElseThrow().chunkCount()).isEqualTo(2);
    }

    @Test
    void changesNothingAndAnnouncesNothingForADocumentThatIsAlreadyIndexed() {
        Document document = givenStoredMarkdown("Guía Davivienda");
        indexDocument.index(document.id());
        Document indexed = documents.findById(document.id()).orElseThrow();
        drainStatusQueue();

        indexDocument.index(document.id());

        Document unchanged = documents.findById(document.id()).orElseThrow();
        assertThat(unchanged.indexedAt()).isEqualTo(indexed.indexedAt());
        assertThat(unchanged.updatedAt()).isEqualTo(indexed.updatedAt());
        assertThat(chunkCountOf(document.id())).isEqualTo(2);
        assertThat(rabbitTemplate.receive(SUBSCRIBER_QUEUE, SILENCE_TIMEOUT_MS)).isNull();
    }

    @Test
    void broadcastsTheStatusOnlyOnceTheRowIsCommitted() {
        Document document = givenStoredMarkdown("Guía Davivienda");

        transactions.execute(status -> {
            indexDocument.index(document.id());

            // Still inside the transaction: no browser may be told about a row nobody else can read.
            assertThat(rabbitTemplate.receive(SUBSCRIBER_QUEUE, SILENCE_TIMEOUT_MS))
                    .isNull();
            return null;
        });

        Message event = rabbitTemplate.receive(SUBSCRIBER_QUEUE, RECEIVE_TIMEOUT_MS);
        assertThat(event).isNotNull();
        assertThat(event.getMessageProperties().getMessageId())
                .isEqualTo(document.id().toString());

        String payload = new String(event.getBody(), StandardCharsets.UTF_8);
        assertThat(payload)
                .contains("\"documentId\":\"" + document.id() + "\"")
                .contains("\"status\":\"INDEXADO\"")
                .contains("occurredAt")
                .doesNotContain("errorCode");
    }

    private void drainStatusQueue() {
        while (rabbitTemplate.receive(SUBSCRIBER_QUEUE, 50) != null) {
            // Left over from a previous test in the shared context.
        }
    }

    @Test
    void givesEveryApiInstanceItsOwnSubscriptionToTheFanOut() {
        assertThat(apiSubscriptionQueue.isExclusive()).isTrue();
        assertThat(apiSubscriptionQueue.isAutoDelete()).isTrue();
        assertThat(amqpAdmin.getQueueProperties(apiSubscriptionQueue.getName())).isNotNull();
    }
}
