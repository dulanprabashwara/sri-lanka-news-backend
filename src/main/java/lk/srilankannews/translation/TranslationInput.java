package lk.srilankannews.translation;

import java.util.Set;
import lk.srilankannews.common.domain.Language;

public record TranslationInput(
        Language sourceLanguage,
        String title,
        String summary,
        Set<Language> targetLanguages) {
    public TranslationInput {
        targetLanguages = Set.copyOf(targetLanguages);
    }
}
