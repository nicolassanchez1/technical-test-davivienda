package io.github.nicolassanchez1.technicaltestdavivienda.documents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DocumentMetadataTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private static DocumentMetadata metadata(String title, String author, String version, List<String> tags) {
        return new DocumentMetadata(title, author, DocumentCategory.MANUAL, tags, version);
    }

    private static DocumentMetadata validMetadata() {
        return metadata("Manual de despliegue", "Equipo de plataforma", "1.0", List.of("despliegue"));
    }

    private static List<String> violatedProperties(DocumentMetadata metadata) {
        return validator.validate(metadata).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .toList();
    }

    @Test
    void acceptsFullyPopulatedMetadata() {
        assertThat(validator.validate(validMetadata())).isEmpty();
    }

    @Test
    void stripsLowerCasesAndDeduplicatesTagsKeepingTheirOrder() {
        DocumentMetadata metadata =
                metadata("Manual", "Equipo", "1.0", List.of(" Kubernetes ", "Docker", "kubernetes", "DOCKER"));

        assertThat(metadata.tags()).containsExactly("kubernetes", "docker");
    }

    @Test
    void dropsBlankAndMissingTags() {
        DocumentMetadata metadata = metadata("Manual", "Equipo", "1.0", Arrays.asList("  ", "", null, "sre"));

        assertThat(metadata.tags()).containsExactly("sre");
    }

    @Test
    void treatsMissingTagsAsNoTags() {
        assertThat(metadata("Manual", "Equipo", "1.0", null).tags()).isEmpty();
    }

    @Test
    void exposesTagsAsAnUnmodifiableList() {
        DocumentMetadata metadata = validMetadata();

        assertThatThrownBy(() -> metadata.tags().add("otro")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsABlankTitle() {
        assertThat(violatedProperties(metadata("   ", "Equipo", "1.0", List.of())))
                .containsExactly("title");
    }

    @Test
    void rejectsATitleBeyondTheLengthCeiling() {
        String tooLong = "t".repeat(DocumentMetadata.TITLE_MAX_LENGTH + 1);

        assertThat(violatedProperties(metadata(tooLong, "Equipo", "1.0", List.of())))
                .containsExactly("title");
    }

    @Test
    void rejectsABlankAuthor() {
        assertThat(violatedProperties(metadata("Manual", "", "1.0", List.of()))).containsExactly("author");
    }

    @Test
    void rejectsAnAuthorBeyondTheLengthCeiling() {
        String tooLong = "a".repeat(DocumentMetadata.AUTHOR_MAX_LENGTH + 1);

        assertThat(violatedProperties(metadata("Manual", tooLong, "1.0", List.of())))
                .containsExactly("author");
    }

    @Test
    void rejectsAMissingCategory() {
        DocumentMetadata metadata = new DocumentMetadata("Manual", "Equipo", null, List.of(), "1.0");

        assertThat(violatedProperties(metadata)).containsExactly("category");
    }

    @Test
    void rejectsABlankVersion() {
        assertThat(violatedProperties(metadata("Manual", "Equipo", " ", List.of())))
                .containsExactly("version");
    }

    @Test
    void rejectsAVersionBeyondTheLengthCeiling() {
        String tooLong = "9".repeat(DocumentMetadata.VERSION_MAX_LENGTH + 1);

        assertThat(violatedProperties(metadata("Manual", "Equipo", tooLong, List.of())))
                .containsExactly("version");
    }

    @Test
    void rejectsMoreTagsThanAllowed() {
        List<String> tooMany = IntStream.rangeClosed(0, DocumentMetadata.TAGS_MAX_COUNT)
                .mapToObj(index -> "tag" + index)
                .toList();

        assertThat(violatedProperties(metadata("Manual", "Equipo", "1.0", tooMany)))
                .containsExactly("tags");
    }

    @Test
    void rejectsATagBeyondTheLengthCeiling() {
        String tooLong = "x".repeat(DocumentMetadata.TAG_MAX_LENGTH + 1);

        assertThat(violatedProperties(metadata("Manual", "Equipo", "1.0", List.of(tooLong))))
                .singleElement()
                .asString()
                .startsWith("tags[0]");
    }
}
