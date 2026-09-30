package io.github.nicolassanchez1.technicaltestdavivienda.shared.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Derives the servlet upload limits from the documented settings instead of repeating them as
 * separate knobs, so raising {@code APP_MAX_FILE_SIZE_MB} or {@code APP_MAX_FILES_PER_UPLOAD}
 * cannot leave the container rejecting a batch the application would have accepted.
 */
@Configuration
class MultipartLimitsConfiguration {

    /** Enough for the part headers and boundaries of a full batch. */
    private static final DataSize REQUEST_HEADROOM = DataSize.ofMegabytes(1);

    /** Spooling to disk almost immediately is what keeps an upload out of the heap. */
    private static final DataSize MEMORY_THRESHOLD = DataSize.ofKilobytes(8);

    @Bean
    MultipartConfigElement multipartConfigElement(AppProperties properties) {
        long maxFileSizeBytes = properties.maxFileSizeBytes();
        long maxRequestSizeBytes = maxFileSizeBytes * properties.maxFilesPerUpload() + REQUEST_HEADROOM.toBytes();

        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofBytes(maxFileSizeBytes));
        factory.setMaxRequestSize(DataSize.ofBytes(maxRequestSizeBytes));
        factory.setFileSizeThreshold(MEMORY_THRESHOLD);
        return factory.createMultipartConfig();
    }
}
