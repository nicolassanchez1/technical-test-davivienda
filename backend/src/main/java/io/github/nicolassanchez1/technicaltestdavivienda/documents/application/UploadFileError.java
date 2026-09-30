package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import java.util.UUID;

/**
 * Why one file of a batch was turned down. The index is the position in the {@code files} part, so
 * a client can line the error up with the row it rendered for that file.
 *
 * @param existingDocumentId the document that already holds the same content, or null when the
 *     rule is not about duplication or when the clash is with another file of the same request
 */
public record UploadFileError(
        int index, String filename, UploadRule rule, DocumentErrorCode errorCode, UUID existingDocumentId) {

    public static UploadFileError rejectedBy(int index, String filename, UploadRule rule) {
        return new UploadFileError(index, filename, rule, rule.errorCode(), null);
    }

    public static UploadFileError duplicateOf(int index, String filename, UUID existingDocumentId) {
        return new UploadFileError(index, filename, UploadRule.UNIQUE_CHECKSUM, null, existingDocumentId);
    }
}
