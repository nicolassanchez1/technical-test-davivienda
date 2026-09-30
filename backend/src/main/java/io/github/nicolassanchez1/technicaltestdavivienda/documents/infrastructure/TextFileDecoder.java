package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads a text file without being told its encoding. The spec accepts documents an editor saved as
 * Latin-1, and reading those as UTF-8 turns every accented character into a replacement character
 * that no search would ever match.
 */
@Component
public class TextFileDecoder {

    private static final Logger log = LoggerFactory.getLogger(TextFileDecoder.class);

    private static final char BYTE_ORDER_MARK = '\uFEFF';

    /**
     * @return the file decoded with its own encoding, byte order mark removed
     * @throws UncheckedIOException when the file could not be read
     */
    public String decode(Path file) {
        byte[] content = readAll(file);
        String text = asUtf8(content).orElseGet(() -> new String(content, detectedCharset(content, file)));
        return text.isEmpty() || text.charAt(0) != BYTE_ORDER_MARK ? text : text.substring(1);
    }

    /**
     * UTF-8 validates itself: its multi-byte sequences are constrained enough that a strict decode
     * succeeding is proof rather than a guess. This has to come first, because statistical detection
     * reads a few hundred bytes of accented Spanish stored as UTF-8 as GB18030, which turns every
     * accent into a Chinese ideograph. The fixture behind
     * {@code PlainTextExtractorTest.readsUtf8TextWithItsAccentedCharacters} is long enough to
     * reproduce it, so reordering these two steps fails that test.
     */
    private static Optional<String> asUtf8(byte[] content) {
        CharsetDecoder strict = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return Optional.of(strict.decode(ByteBuffer.wrap(content)).toString());
        } catch (CharacterCodingException notUtf8) {
            return Optional.empty();
        }
    }

    /**
     * What is left is a single-byte encoding, which is the case detection is good at. A file it
     * cannot place still has to be read as something, and UTF-8 is what a text file carries unless
     * it says otherwise.
     */
    private static Charset detectedCharset(byte[] content, Path file) {
        UniversalDetector detector = new UniversalDetector();
        detector.handleData(content, 0, content.length);
        detector.dataEnd();
        String detected = detector.getDetectedCharset();
        log.debug("Decoding {} as {}", file.getFileName(), detected == null ? "UTF-8 by default" : detected);
        if (detected == null) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName(detected, StandardCharsets.UTF_8);
    }

    private static byte[] readAll(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException cause) {
            throw new UncheckedIOException("Could not read " + file.getFileName(), cause);
        }
    }
}
