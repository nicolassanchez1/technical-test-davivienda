package io.github.nicolassanchez1.technicaltestdavivienda.documents.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Accepted upload. Indexing continues in the background.")
public record UploadAcceptedResponse(List<UploadedDocumentResponse> items) {}
