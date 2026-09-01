package lk.srilankannews.translation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;

class TranslationOutputValidatorTest {
    private final TranslationOutputValidator validator = new TranslationOutputValidator();

    @Test
    void rejectsMissingDuplicateUnexpectedAndBlankTranslations() {
        Set<Language> requested = Set.of(Language.SI, Language.TA);
        assertThatThrownBy(() -> validator.validate(
                List.of(new TranslatedContent(Language.SI, "සිංහල", "සාරාංශය")),
                requested, true)).isInstanceOf(TranslationProviderException.class);
        assertThatThrownBy(() -> validator.validate(List.of(
                new TranslatedContent(Language.SI, "සිංහල", "සාරාංශය"),
                new TranslatedContent(Language.SI, "නැවත", "සාරාංශය")),
                requested, true)).isInstanceOf(TranslationProviderException.class);
        assertThatThrownBy(() -> validator.validate(List.of(
                new TranslatedContent(Language.EN, "English", "Summary")),
                Set.of(Language.SI), true)).isInstanceOf(TranslationProviderException.class);
        assertThatThrownBy(() -> validator.validate(List.of(
                new TranslatedContent(Language.SI, " ", "සාරාංශය")),
                Set.of(Language.SI), true)).isInstanceOf(TranslationProviderException.class);
    }
}
