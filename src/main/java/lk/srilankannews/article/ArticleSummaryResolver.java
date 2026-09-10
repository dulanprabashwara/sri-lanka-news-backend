package lk.srilankannews.article;

import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ArticleSummaryResolver {
    private static final int MAX_CHARACTERS = 400;
    private static final int MAX_SENTENCES = 2;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern BYLINE = Pattern.compile(
            "(?iu)^(?:by|written by|reported by|reporting by|author)\\s*[:\\-]?\\s+.{2,100}$");
    private static final Pattern JUNK_LINE = Pattern.compile(
            "(?iu)^(?:continue reading|read more|advertisement|click here(?: to read more)?|"
                    + "subscribe(?: now)?|share this article)[\\p{Punct}\\s]*$");
    private static final Pattern CONTINUE_READING = Pattern.compile(
            "(?iu)\\bcontinue\\s+reading\\b[\\p{Punct}\\s]*");

    public ResolvedSummary resolve(Article article) {
        String publisher = usable(article.summary());
        if (publisher != null) {
            return new ResolvedSummary(publisher, SummarySource.PUBLISHER);
        }
        String ai = article.aiEnrichment() == null
                ? null
                : usable(article.aiEnrichment().summary());
        if (ai != null) {
            return new ResolvedSummary(ai, SummarySource.AI);
        }
        String extractive = extract(article.title(), article.extractedContent());
        return extractive == null
                ? null
                : new ResolvedSummary(extractive, SummarySource.EXTRACTIVE);
    }

    private String extract(String title, String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String normalizedTitle = comparisonKey(title);
        Set<String> uniqueLines = new HashSet<>();
        StringBuilder cleaned = new StringBuilder();
        for (String rawLine : Normalizer.normalize(content, Normalizer.Form.NFC).split("\\R+")) {
            String line = normalize(rawLine);
            if (line.isBlank() || BYLINE.matcher(line).matches() || JUNK_LINE.matcher(line).matches()) {
                continue;
            }
            line = normalize(CONTINUE_READING.matcher(line).replaceAll(""));
            String key = comparisonKey(line);
            if (line.isBlank() || key.equals(normalizedTitle) || !uniqueLines.add(key)) {
                continue;
            }
            if (!cleaned.isEmpty()) {
                cleaned.append(' ');
            }
            cleaned.append(line);
        }
        String text = normalize(cleaned.toString());
        if (text.isBlank()) {
            return null;
        }

        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ROOT);
        iterator.setText(text);
        Set<String> uniqueSentences = new HashSet<>();
        StringBuilder result = new StringBuilder();
        int sentenceCount = 0;
        for (int start = iterator.first(), end = iterator.next();
                end != BreakIterator.DONE && sentenceCount < MAX_SENTENCES;
                start = end, end = iterator.next()) {
            String sentence = normalize(text.substring(start, end));
            String key = comparisonKey(sentence);
            if (sentence.length() < 20 || key.equals(normalizedTitle)
                    || !uniqueSentences.add(key)) {
                continue;
            }
            appendWithinLimit(result, sentence);
            sentenceCount++;
            if (result.length() >= MAX_CHARACTERS) {
                break;
            }
        }
        if (result.isEmpty()) {
            appendWithinLimit(result, text);
        }
        return result.isEmpty() ? null : result.toString();
    }

    private void appendWithinLimit(StringBuilder result, String text) {
        int separator = result.isEmpty() ? 0 : 1;
        int remaining = MAX_CHARACTERS - result.length() - separator;
        if (remaining <= 0) {
            return;
        }
        String addition = text;
        if (addition.length() > remaining) {
            addition = cutAtWord(addition, remaining);
        }
        if (addition.isBlank()) {
            return;
        }
        if (separator == 1) {
            result.append(' ');
        }
        result.append(addition);
    }

    private String cutAtWord(String value, int maximum) {
        if (value.length() <= maximum) {
            return value;
        }
        int contentLimit = Math.max(1, maximum - 1);
        int boundary = value.lastIndexOf(' ', contentLimit);
        if (boundary < Math.min(20, contentLimit)) {
            boundary = contentLimit;
        }
        return value.substring(0, boundary).stripTrailing() + "\u2026";
    }

    private String usable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return WHITESPACE.matcher(Normalizer.normalize(value, Normalizer.Form.NFC)
                        .replace('\u00a0', ' ')
                        .replace("\u200b", ""))
                .replaceAll(" ")
                .trim();
    }

    private String comparisonKey(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }

    public record ResolvedSummary(String text, SummarySource source) {
    }

    public enum SummarySource {
        PUBLISHER,
        AI,
        EXTRACTIVE
    }
}
