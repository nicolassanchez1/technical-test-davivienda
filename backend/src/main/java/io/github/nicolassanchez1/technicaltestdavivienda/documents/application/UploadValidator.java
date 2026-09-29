package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Decides whether an uploaded file may enter the pipeline, before a single byte is stored. It only
 * needs the declared size and a prefix of the content, so an oversized or binary file is turned
 * down without being read whole.
 */
@Component
public class UploadValidator {

    /**
     * How many leading bytes callers should hand over: enough for a header and a binary check. More
     * than this is ignored, so validating a large upload never costs more than reading its prefix.
     */
    public static final int INSPECTED_PREFIX_BYTES = 8 * 1024;

    private static final String PDF_HEADER = "%PDF-";

    private final long maxFileSizeBytes;

    public UploadValidator(AppProperties properties) {
        this.maxFileSizeBytes = properties.maxFileSizeBytes();
    }

    public UploadValidation validate(String filename, long declaredSizeBytes, byte[] leadingBytes) {
        Optional<UploadFileType> resolved = UploadFileType.ofFilename(filename);
        if (resolved.isEmpty()) {
            return UploadValidation.rejected(UploadRule.EXTENSION_ALLOWLIST);
        }
        if (declaredSizeBytes <= 0) {
            return UploadValidation.rejected(UploadRule.NON_EMPTY_FILE);
        }
        if (declaredSizeBytes > maxFileSizeBytes) {
            return UploadValidation.rejected(UploadRule.MAXIMUM_FILE_SIZE);
        }

        UploadFileType fileType = resolved.get();
        byte[] prefix = leadingBytes == null ? new byte[0] : leadingBytes;
        return switch (fileType) {
            case PDF ->
                carriesThePdfHeader(prefix)
                        ? UploadValidation.accepted(fileType)
                        : UploadValidation.rejected(UploadRule.PDF_HEADER);
            case PLAIN_TEXT, MARKDOWN ->
                readsAsText(prefix)
                        ? UploadValidation.accepted(fileType)
                        : UploadValidation.rejected(UploadRule.TEXT_WITHOUT_BINARY_BYTES);
        };
    }

    private static boolean carriesThePdfHeader(byte[] prefix) {
        byte[] header = PDF_HEADER.getBytes(StandardCharsets.US_ASCII);
        return prefix.length >= header.length && Arrays.equals(prefix, 0, header.length, header, 0, header.length);
    }

    /**
     * A text upload may hold printable bytes plus ordinary whitespace and nothing else. A NUL or
     * another control byte means the file is binary. The encoding itself is not asserted here: the
     * spec accepts Latin-1 content, which a strict UTF-8 decode would reject, so the extractor
     * detects the charset later.
     */
    private static boolean readsAsText(byte[] prefix) {
        int inspected = Math.min(prefix.length, INSPECTED_PREFIX_BYTES);
        for (int index = 0; index < inspected; index++) {
            if (isBinaryControlByte(prefix[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBinaryControlByte(byte value) {
        return value >= 0 && value < 0x20 && value != '\t' && value != '\n' && value != '\r' && value != '\f';
    }
}
