package io.github.nicolassanchez1.technicaltestdavivienda.documents.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One page of documents, newest first")
public record DocumentPageResponse(List<DocumentResponse> items, long total, int page, int pageSize) {}
