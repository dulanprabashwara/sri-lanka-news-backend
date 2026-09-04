package lk.srilankannews.source;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CreateSourceCommandValidationTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsAValidSource() {
        CreateSourceCommand command = validCommand();

        assertThat(validator.validate(command)).isEmpty();
    }

    @Test
    void rejectsInvalidRequiredFieldsSlugAndUrl() {
        CreateSourceCommand command = new CreateSourceCommand("", "Daily Mirror", "ftp://example.com/news", null, null, true, new lk.srilankannews.source.SourceImagePolicy(false, java.util.Set.of()));

        Set<String> invalidFields = validator.validate(command).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(invalidFields)
                .contains("name", "slug", "baseUrl", "defaultLanguage", "ingestionType");
    }

    private CreateSourceCommand validCommand() {
        return new CreateSourceCommand(
                "Daily Mirror",
                "daily-mirror",
                "https://www.dailymirror.lk",
                Language.EN,
                IngestionType.RSS,
                true,
                    new lk.srilankannews.source.SourceImagePolicy(false, java.util.Set.of()));
    }
}
