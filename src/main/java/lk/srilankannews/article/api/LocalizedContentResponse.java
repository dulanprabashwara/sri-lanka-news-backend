package lk.srilankannews.article.api;

import lk.srilankannews.common.domain.Language;

public record LocalizedContentResponse(
        Language requestedLanguage,
        Language resolvedLanguage,
        boolean translated,
        boolean fallback,
        String title,
        String summary) {
}
