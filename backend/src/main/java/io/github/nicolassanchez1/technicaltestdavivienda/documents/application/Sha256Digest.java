package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The checksum that makes a duplicate upload detectable. It is computed by streaming the file, so
 * the content is never buffered in memory; the upload opens the file again for each pass it needs.
 */
public final class Sha256Digest {

    private static final String ALGORITHM = "SHA-256";

    private final MessageDigest digest;
    private String hex;

    public Sha256Digest() {
        this.digest = newDigest();
    }

    /** Wraps the stream so every byte read through it feeds the digest. */
    public InputStream wrap(InputStream content) {
        return new DigestInputStream(content, digest);
    }

    /** Lower-case hex, 64 characters. Read it once the wrapped stream has been consumed. */
    public String hex() {
        if (hex == null) {
            hex = HexFormat.of().formatHex(digest.digest());
        }
        return hex;
    }

    /** Drains the stream and returns its checksum, for callers that only need the digest. */
    public static String of(InputStream content) throws IOException {
        Sha256Digest sha256 = new Sha256Digest();
        try (InputStream digesting = sha256.wrap(content)) {
            digesting.transferTo(OutputStream.nullOutputStream());
        }
        return sha256.hex();
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException cause) {
            throw new IllegalStateException(ALGORITHM + " is required of every Java platform", cause);
        }
    }
}
