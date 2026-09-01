package lk.srilankannews.translation;

import lk.srilankannews.common.domain.Language;

public record TranslatedContent(Language language, String title, String summary) {
}
