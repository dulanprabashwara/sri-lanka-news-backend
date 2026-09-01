package lk.srilankannews.translation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lk.srilankannews.common.domain.Language;
import org.springframework.stereotype.Component;

@Component
public class TranslationOutputValidator {
    static final int MAX_TITLE_CHARACTERS = 1000;
    static final int MAX_SUMMARY_CHARACTERS = 5000;

    public Map<Language, TranslatedContent> validate(
            List<TranslatedContent> output,
            Set<Language> requested,
            boolean summaryRequired) {
        if (output == null) {
            throw new TranslationProviderException("Translation provider returned no output.");
        }
        Map<Language, TranslatedContent> validated = new EnumMap<>(Language.class);
        for (TranslatedContent item : output) {
            if (item == null || item.language() == null || !requested.contains(item.language())) {
                throw new TranslationProviderException("Translation provider returned an unexpected language.");
            }
            if (validated.containsKey(item.language())) {
                throw new TranslationProviderException("Translation provider returned a duplicate language.");
            }
            String title = requireText(item.title(), MAX_TITLE_CHARACTERS, "title");
            String summary = item.summary();
            if (summaryRequired) {
                summary = requireText(summary, MAX_SUMMARY_CHARACTERS, "summary");
            } else if (summary != null && summary.length() > MAX_SUMMARY_CHARACTERS) {
                throw new TranslationProviderException("Translated summary exceeds the maximum length.");
            }
            validated.put(item.language(), new TranslatedContent(item.language(), title, summary));
        }
        if (!validated.keySet().equals(requested)) {
            throw new TranslationProviderException("Translation provider omitted a requested language.");
        }
        return Map.copyOf(validated);
    }

    private String requireText(String value, int maximum, String field) {
        if (value == null || value.isBlank()) {
            throw new TranslationProviderException("Translated " + field + " is blank.");
        }
        String cleaned = value.trim();
        if (cleaned.length() > maximum) {
            throw new TranslationProviderException("Translated " + field + " exceeds the maximum length.");
        }
        return cleaned;
    }
}
