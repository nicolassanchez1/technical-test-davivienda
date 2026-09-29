package io.github.nicolassanchez1.technicaltestdavivienda.shared.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings, bound from {@code APP_*} environment variables. Every bound value is
 * constrained so a wrong deployment fails at boot instead of at the first request.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull Path storageDir,
        @Positive @Max(1024) int maxFileSizeMb,
        @Positive @Max(100) int maxFilesPerUpload,
        @Positive @Max(60_000) long searchTimeoutMs,
        @Positive @Max(200) int searchMaxPageSize,
        @Positive @Max(64) int workerConcurrency,
        @Positive @Max(1440) int stuckProcessingMinutes,
        @Positive @Max(300_000) long sseHeartbeatMs) {

    public long maxFileSizeBytes() {
        return (long) maxFileSizeMb * 1024 * 1024;
    }
}
