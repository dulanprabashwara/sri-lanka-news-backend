package lk.srilankannews.translation;

import lk.srilankannews.common.domain.Language;

public record TranslatedContent(
        Language language,
        String title,
        String summary,
        String provider,
        String model) {

    public TranslatedContent(Language language, String title, String summary) {
        this(language, title, summary, null, null);
    }
}
