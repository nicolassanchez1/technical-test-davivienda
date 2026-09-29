package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;

/**
 * The verdict on one uploaded file: either the resolved format, or the rule that turned it down.
 * Exactly one of the two components is set.
 */
public record UploadValidation(UploadFileType fileType, UploadRule failedRule) {

    public static UploadValidation accepted(UploadFileType fileType) {
        return new UploadValidation(fileType, null);
    }

    public static UploadValidation rejected(UploadRule failedRule) {
        return new UploadValidation(null, failedRule);
    }

    public boolean isAccepted() {
        return failedRule == null;
    }

    /** The error code matching the failed rule, or null when the file was accepted. */
    public DocumentErrorCode errorCode() {
        return failedRule == null ? null : failedRule.errorCode();
    }
}
