package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalFileStorageTest {

    private static final String UPLOADED_FILENAME = "informe-confidencial.txt";

    @TempDir
    Path root;

    private Path storageDir;
    private LocalFileStorage storage;

    private static AppProperties propertiesFor(Path storageDir) {
        return new AppProperties(storageDir, 20, 10, 900L, 50, 4, 10, 15_000L, 3_600_000L);
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void createStorage() {
        storageDir = root.resolve("uploads");
        storage = new LocalFileStorage(propertiesFor(storageDir));
    }

    @Test
    void createsTheStorageDirectoryWhenItIsMissing() {
        Path missing = root.resolve("nested").resolve("uploads");

        new LocalFileStorage(propertiesFor(missing));

        assertThat(missing).isDirectory();
    }

    @Test
    void namesTheStoredFileAfterAFreshIdentifierAndNeverAfterTheUpload() throws IOException {
        String storageKey = storage.store(stream("contenido"), "txt");

        assertThat(storageKey)
                .doesNotContain(UPLOADED_FILENAME)
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.txt");
        try (Stream<Path> stored = Files.list(storageDir)) {
            assertThat(stored).singleElement().satisfies(file -> assertThat(file.getFileName())
                    .hasToString(storageKey));
        }
    }

    @Test
    void writesTheContentItWasGiven() throws IOException {
        String storageKey = storage.store(stream("Especificación técnica"), "txt");

        assertThat(Files.readString(storage.resolve(storageKey), StandardCharsets.UTF_8))
                .isEqualTo("Especificación técnica");
    }

    @Test
    void givesEveryUploadItsOwnKeyEvenForIdenticalContent() {
        String first = storage.store(stream("contenido"), "md");
        String second = storage.store(stream("contenido"), "md");

        assertThat(first).isNotEqualTo(second);
        assertThat(storage.resolve(first)).exists();
        assertThat(storage.resolve(second)).exists();
    }

    @Test
    void normalisesTheExtensionItIsGiven() {
        assertThat(storage.store(stream("contenido"), ".PDF")).endsWith(".pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ".", "no/pe", "../", "txt.", "toolongextensionvalue"})
    void refusesAnExtensionThatIsNotASimpleSuffix(String extension) {
        assertThatThrownBy(() -> storage.store(stream("contenido"), extension))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "../../etc/passwd",
                "../1f0d8d0e-6b1a-4f2c-9a4d-0c7e9f5b1a23.txt",
                "/etc/passwd",
                "nested/1f0d8d0e-6b1a-4f2c-9a4d-0c7e9f5b1a23.txt",
                "1f0d8d0e-6b1a-4f2c-9a4d-0c7e9f5b1a23.txt/../../secret",
                "informe-confidencial.txt",
                "1F0D8D0E-6B1A-4F2C-9A4D-0C7E9F5B1A23.txt",
                "1f0d8d0e-6b1a-4f2c-9a4d-0c7e9f5b1a23"
            })
    void refusesAStorageKeyItDidNotGenerate(String storageKey) {
        assertThatThrownBy(() -> storage.resolve(storageKey)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete(storageKey)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAMissingStorageKey() {
        assertThatThrownBy(() -> storage.resolve(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolvesAStoredKeyInsideTheStorageDirectory() {
        String storageKey = storage.store(stream("contenido"), "txt");

        assertThat(storage.resolve(storageKey))
                .exists()
                .hasParent(storageDir.toAbsolutePath().normalize());
    }

    @Test
    void deletesAStoredFileAndToleratesASecondDelete() {
        String storageKey = storage.store(stream("contenido"), "txt");

        storage.delete(storageKey);
        storage.delete(storageKey);

        assertThat(storage.resolve(storageKey)).doesNotExist();
    }
}
