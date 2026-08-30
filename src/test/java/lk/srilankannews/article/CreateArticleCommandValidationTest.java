package lk.srilankannews.article;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CreateArticleCommandValidationTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsAValidArticleWithOptionalCategory() {
        assertThat(validator.validate(validCommand())).isEmpty();
    }

    @Test
    void rejectsMissingFieldsInvalidUrlsAndBlankAuthor() {
        CreateArticleCommand command = new CreateArticleCommand(
                "",
                "",
                "file:///article.html",
                "not-a-url",
                null,
                List.of(""),
                null,
                null,
                null,
                null);

        Set<String> invalidFields = validator.validate(command).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(invalidFields)
                .contains("sourceId", "title", "originalUrl", "canonicalUrl",
                        "originalLanguage", "authors[0].<list element>", "publishedAt", "discoveredAt",
                        "extractedContent");
    }

    private CreateArticleCommand validCommand() {
        Instant publishedAt = Instant.parse("2020-08-30T09:00:00Z");
        return new CreateArticleCommand(
                "source-1",
                "Sri Lanka news headline",
                "https://example.com/news/article?utm_source=feed",
                "https://example.com/news/article",
                Language.EN,
                List.of("Reporter One"),
                publishedAt,
                publishedAt.plusSeconds(60),
                ArticleCategory.LOCAL,
                "Clean fixture body");
    }
}
