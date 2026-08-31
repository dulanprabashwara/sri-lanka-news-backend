package lk.srilankannews.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import org.junit.jupiter.api.Test;

class AiOutputValidatorTest {
    private final AiOutputValidator validator = new AiOutputValidator();

    @Test
    void trimsAndDefensivelyValidatesStructuredOutput() {
        AiResult result = validator.validate(new AiResult(
                " Summary ",
                ArticleCategory.LOCAL,
                List.of(" Sri Lanka ", "Sri Lanka"),
                List.of(" news "),
                List.of(new AiEntity(" Colombo ", " LOCATION "))));

        assertThat(result.summary()).isEqualTo("Summary");
        assertThat(result.topics()).containsExactly("Sri Lanka");
        assertThat(result.entities()).containsExactly(new AiEntity("Colombo", "LOCATION"));
    }

    @Test
    void rejectsMissingAndOversizedStructuredOutput() {
        assertThatThrownBy(() -> validator.validate(new AiResult(
                "", ArticleCategory.LOCAL, List.of(), List.of(), List.of())))
                .isInstanceOf(AiProviderException.class);

        assertThatThrownBy(() -> validator.validate(new AiResult(
                "Summary",
                ArticleCategory.LOCAL,
                java.util.Collections.nCopies(AiOutputValidator.MAX_TOPICS + 1, "topic"),
                List.of(),
                List.of())))
                .isInstanceOf(AiProviderException.class);
    }
}
