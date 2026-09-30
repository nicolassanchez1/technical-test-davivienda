package io.github.nicolassanchez1.technicaltestdavivienda.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/** Access to the sample documents under {@code src/test/resources/fixtures}. */
public final class Fixtures {

    private Fixtures() {}

    public static Path path(String name) {
        URL resource = Fixtures.class.getClassLoader().getResource("fixtures/" + name);
        if (resource == null) {
            throw new IllegalArgumentException("There is no fixture named " + name);
        }
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException cause) {
            throw new IllegalStateException("Could not locate the fixture " + name, cause);
        }
    }

    public static byte[] bytes(String name) {
        try {
            return Files.readAllBytes(path(name));
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }
}
