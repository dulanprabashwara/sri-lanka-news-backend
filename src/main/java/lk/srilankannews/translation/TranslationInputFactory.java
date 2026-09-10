package lk.srilankannews.translation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleSummaryResolver;
import org.springframework.stereotype.Component;

@Component
public class TranslationInputFactory {
    private final ArticleSummaryResolver summaryResolver;

    public TranslationInputFactory(ArticleSummaryResolver summaryResolver) {
        this.summaryResolver = summaryResolver;
    }

    public PreparedTranslationInput prepare(Article article, TranslationProperties properties) {
        String title = normalize(article.title());
        ArticleSummaryResolver.ResolvedSummary resolvedSummary = summaryResolver.resolve(article);
        String summary = normalize(resolvedSummary == null ? null : resolvedSummary.text());
        String material = "translation-input|" + properties.promptVersion()
                + "|" + article.originalLanguage().code() + "|" + title + "|" + summary;
        return new PreparedTranslationInput(title, summary, sha256(material));
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record PreparedTranslationInput(String title, String summary, String inputHash) {
    }
}
