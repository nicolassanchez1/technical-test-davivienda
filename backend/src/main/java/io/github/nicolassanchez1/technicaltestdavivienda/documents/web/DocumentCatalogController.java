package io.github.nicolassanchez1.technicaltestdavivienda.documents.web;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentCatalog;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentContent;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentPage;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.StoredFile;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.web.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reads documents back: the list, one document, its body and its original file. */
@RestController
@RequestMapping("/documents")
public class DocumentCatalogController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_CONTENT_WINDOW = 50;

    /** Paging deeper than this is never a real reader, and it costs the database a full sort. */
    private static final long MAX_OFFSET = 10_000;

    private final DocumentCatalog catalog;
    private final int maxPageSize;

    public DocumentCatalogController(DocumentCatalog catalog, AppProperties properties) {
        this.catalog = catalog;
        this.maxPageSize = properties.searchMaxPageSize();
    }

    @GetMapping
    public DocumentPageResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize) {

        int requestedPage = requirePositive(page);
        int size = boundedPageSize(pageSize);
        DocumentPage result = catalog.list(statusFilter(status), size, offsetOf(requestedPage, size));

        return new DocumentPageResponse(
                result.items().stream().map(DocumentResponse::of).toList(), result.total(), requestedPage, size);
    }

    @GetMapping("/{id}")
    public DocumentResponse byId(@PathVariable UUID id) {
        return catalog.byId(id).map(DocumentResponse::of).orElseThrow(() -> notFound(id));
    }

    @Operation(summary = "Read a document's body", description = "Chunks in order, paged by a cursor over chunkIndex.")
    @GetMapping("/{id}/content")
    public DocumentContentResponse content(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int fromChunkIndex,
            @RequestParam(defaultValue = "" + DEFAULT_CONTENT_WINDOW) int limit) {

        DocumentContent content = catalog.content(id, Math.max(fromChunkIndex, 0), boundedPageSize(limit))
                .orElseThrow(() -> notFound(id));

        return new DocumentContentResponse(
                content.chunks().stream().map(DocumentChunkResponse::of).toList(), content.nextChunkIndex());
    }

    @Operation(summary = "Read the original file", description = "Served inline so the browser renders it in place.")
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable UUID id) {
        StoredFile stored = catalog.file(id).orElseThrow(() -> notFound(id));
        Path path = stored.path();
        if (!Files.isReadable(path)) {
            throw notFound(id);
        }

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(stored.document().originalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(stored.document().mimeType()))
                .body(new FileSystemResource(path));
    }

    /**
     * Resolved here rather than through a registered converter: when a converter rejects a value
     * Spring falls back to matching the enum constant name, which would quietly let the internal
     * English names into a contract that speaks the Spanish wire values.
     */
    private static DocumentStatus statusFilter(String status) {
        return status == null || status.isBlank() ? null : DocumentStatus.fromWireValue(status.trim());
    }

    private int boundedPageSize(int requested) {
        return Math.clamp(requested, 1, maxPageSize);
    }

    private static int requirePositive(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("page starts at 1");
        }
        return page;
    }

    /**
     * Computed in long arithmetic and capped: {@code (page - 1) * size} overflows an int for a far
     * enough page, and a negative offset makes PostgreSQL raise an error that would surface as a
     * conflict rather than as the bad request it is.
     */
    private static int offsetOf(int page, int size) {
        long offset = (long) (page - 1) * size;
        if (offset > MAX_OFFSET) {
            throw new IllegalArgumentException("page is beyond the deepest page that can be served");
        }
        return (int) offset;
    }

    private static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Document " + id + " does not exist.");
    }
}
