package io.github.nicolassanchez1.technicaltestdavivienda.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class AppPropertiesTest {

    private static final String[] VALID_PROPERTIES = {
        "app.storage-dir=/var/lib/documents",
        "app.max-file-size-mb=20",
        "app.max-files-per-upload=10",
        "app.search-timeout-ms=900",
        "app.search-max-page-size=50",
        "app.worker-concurrency=4",
        "app.stuck-processing-minutes=10",
        "app.sse-heartbeat-ms=15000",
        "app.sse-timeout-ms=3600000"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsEveryDocumentedSetting() {
        runner.withPropertyValues(VALID_PROPERTIES).run(context -> {
            AppProperties properties = context.getBean(AppProperties.class);

            assertThat(properties.storageDir()).isEqualTo(Path.of("/var/lib/documents"));
            assertThat(properties.maxFileSizeMb()).isEqualTo(20);
            assertThat(properties.maxFilesPerUpload()).isEqualTo(10);
            assertThat(properties.searchTimeoutMs()).isEqualTo(900);
            assertThat(properties.searchMaxPageSize()).isEqualTo(50);
            assertThat(properties.workerConcurrency()).isEqualTo(4);
            assertThat(properties.stuckProcessingMinutes()).isEqualTo(10);
            assertThat(properties.sseHeartbeatMs()).isEqualTo(15000);
            assertThat(properties.sseTimeoutMs()).isEqualTo(3600000);
        });
    }

    @Test
    void exposesTheUploadCeilingInBytes() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .run(context -> assertThat(context.getBean(AppProperties.class).maxFileSizeBytes())
                        .isEqualTo(20L * 1024 * 1024));
    }

    @Test
    void failsFastWhenARequiredSettingIsMissing() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenTheUploadSizeIsNotPositive() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("app.max-file-size-mb=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenTheSearchTimeoutExceedsTheAllowedCeiling() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("app.search-timeout-ms=120000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenThePageSizeCeilingIsUnreasonable() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("app.search-max-page-size=5000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenAnEventStreamWouldCloseBeforeItsFirstHeartbeat() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("app.sse-timeout-ms=15000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfiguration {}
}
