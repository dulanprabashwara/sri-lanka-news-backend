package lk.srilankannews.ai;

import lk.srilankannews.common.domain.Language;

public record AiInput(String title, String content, Language language) {
}
