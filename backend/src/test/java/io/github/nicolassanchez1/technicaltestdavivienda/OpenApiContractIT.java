package io.github.nicolassanchez1.technicaltestdavivienda;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

/**
 * Exports the OpenAPI document that the TypeScript contract in packages/shared is generated
 * from. The Java DTOs are the single source of truth; CI regenerates and fails on any diff.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiContractIT {

    private static final String DEFAULT_OUTPUT = "../packages/shared/openapi.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void exportsTheOpenApiDocument() throws IOException {
        String document = RestClient.create("http://localhost:" + port)
                .get()
                .uri("/api/v3/api-docs")
                .retrieve()
                .body(String.class);

        ObjectNode root = (ObjectNode) objectMapper.readTree(document);
        // The generated server URL carries the random test port, which would make the
        // exported contract differ on every run.
        root.remove("servers");

        Path output = Path.of(System.getProperty("contract.output", DEFAULT_OUTPUT));
        Files.createDirectories(output.getParent());
        Files.writeString(output, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n");

        assertThat(root.path("openapi").asText()).startsWith("3.");
        assertThat(output).exists();
    }
}
