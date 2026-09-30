package io.github.nicolassanchez1.technicaltestdavivienda.documents.web;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Tracking handle for one accepted file")
public record UploadedDocumentResponse(UUID id, String filename, DocumentStatus status) {

    public static UploadedDocumentResponse of(Document document) {
        return new UploadedDocumentResponse(document.id(), document.originalFilename(), document.status());
    }
}
