package io.github.nicolassanchez1.technicaltestdavivienda.documents.web;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.InvalidUploadException;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.UploadDocuments;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.UploadedFile;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentMetadata;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.web.ProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Accepts documents. Reading them back is the catalog's job. */
@RestController
@RequestMapping("/documents")
public class DocumentUploadController {

    private final UploadDocuments uploadDocuments;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public DocumentUploadController(UploadDocuments uploadDocuments, ObjectMapper objectMapper, Validator validator) {
        this.uploadDocuments = uploadDocuments;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Operation(
            summary = "Upload one or more documents",
            description = "Answers immediately with a tracking id per file; indexing continues in the background.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Accepted; every file is now PROCESANDO"),
        @ApiResponse(
                responseCode = "413",
                description = "A file exceeds the configured maximum size",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "The batch was rejected; nothing was stored",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemResponse.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadAcceptedResponse> upload(
            @RequestPart("files") List<MultipartFile> files, @RequestPart("metadata") String metadata) {

        List<Document> stored = uploadDocuments.upload(toUploadedFiles(files), parseMetadata(metadata));
        UploadAcceptedResponse body = new UploadAcceptedResponse(
                stored.stream().map(UploadedDocumentResponse::of).toList());

        ResponseEntity.BodyBuilder accepted = ResponseEntity.accepted();
        if (stored.size() == 1) {
            accepted = accepted.location(
                    URI.create("/api/documents/" + stored.getFirst().id()));
        }
        return accepted.body(body);
    }

    private List<UploadedFile> toUploadedFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new InvalidUploadException("An upload must carry at least one file.");
        }
        return files.stream()
                .map(file -> new UploadedFile(originalFilename(file), file.getSize(), () -> {
                    try {
                        return file.getInputStream();
                    } catch (IOException cause) {
                        throw new UncheckedIOException(cause);
                    }
                }))
                .toList();
    }

    private static String originalFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "unnamed" : name;
    }

    private List<DocumentMetadata> parseMetadata(String metadata) {
        List<DocumentMetadata> entries;
        try {
            entries = objectMapper.readValue(metadata, new TypeReference<List<DocumentMetadata>>() {});
        } catch (JacksonException cause) {
            throw new InvalidUploadException("The metadata part is not a JSON array of document metadata.");
        }
        if (entries == null) {
            throw new InvalidUploadException("The metadata part is empty.");
        }
        entries.forEach(this::requireValid);
        return entries;
    }

    private void requireValid(DocumentMetadata entry) {
        Set<ConstraintViolation<DocumentMetadata>> violations = validator.validate(entry);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new InvalidUploadException("The metadata is not valid: " + detail);
        }
    }
}
