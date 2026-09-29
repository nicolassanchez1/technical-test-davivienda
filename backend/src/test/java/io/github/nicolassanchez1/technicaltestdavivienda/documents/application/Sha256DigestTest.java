package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Sha256DigestTest {

    private static final String SHA256_OF_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String SHA256_OF_NOTHING = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void matchesTheKnownVectorForAbc() throws IOException {
        assertThat(Sha256Digest.of(stream("abc"))).isEqualTo(SHA256_OF_ABC);
    }

    @Test
    void matchesTheKnownVectorForAnEmptyStream() throws IOException {
        assertThat(Sha256Digest.of(stream(""))).isEqualTo(SHA256_OF_NOTHING);
    }

    @Test
    void producesSixtyFourLowerCaseHexCharacters() throws IOException {
        String hex = Sha256Digest.of(stream("Especificación técnica"));

        assertThat(hex).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void digestsTheBytesReadThroughTheWrappedStream() throws IOException {
        Sha256Digest digest = new Sha256Digest();

        byte[] read;
        try (InputStream digesting = digest.wrap(stream("abc"))) {
            read = digesting.readAllBytes();
        }

        assertThat(read).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
        assertThat(digest.hex()).isEqualTo(SHA256_OF_ABC);
    }

    @Test
    void returnsTheSameChecksumEveryTimeItIsRead() throws IOException {
        Sha256Digest digest = new Sha256Digest();
        try (InputStream digesting = digest.wrap(stream("abc"))) {
            digesting.readAllBytes();
        }

        assertThat(digest.hex()).isEqualTo(digest.hex()).isEqualTo(SHA256_OF_ABC);
    }
}
