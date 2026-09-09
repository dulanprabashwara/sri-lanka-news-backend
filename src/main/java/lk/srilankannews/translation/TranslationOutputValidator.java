package lk.srilankannews.translation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.text.Normalizer;
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
        return validate(output, requested, summaryRequired, null);
    }

    public Map<Language, TranslatedContent> validate(
            List<TranslatedContent> output,
            Set<Language> requested,
            boolean summaryRequired,
            TranslationInput source) {
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
            if (source != null && sameText(title, source.title())) {
                throw new TranslationProviderException(
                        TranslationProviderException.Kind.INVALID_RESPONSE,
                        "Translated title is identical to the source text.");
            }
            validated.put(item.language(), new TranslatedContent(
                    item.language(), title, summary, item.provider(), item.model()));
        }
        if (!validated.keySet().equals(requested)) {
            throw new TranslationProviderException("Translation provider omitted a requested language.");
        }
        return Map.copyOf(validated);
    }

    private boolean sameText(String first, String second) {
        return normalize(first).equals(normalize(second));
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC)
                .replaceAll("\\s+", " ")
                .trim();
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
