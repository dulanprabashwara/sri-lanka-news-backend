package lk.srilankannews.story;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleEntity;
import lk.srilankannews.article.ArticleSummaryResolver;
import lk.srilankannews.ai.SemanticSimilarityEmbeddingInput;
import org.springframework.stereotype.Component;

@Component
class SemanticEmbeddingInputFactory {
    private final StoryEmbeddingProperties properties;
    private final ArticleSummaryResolver summaryResolver;

    SemanticEmbeddingInputFactory(StoryEmbeddingProperties properties) {
        this(properties, new ArticleSummaryResolver());
    }

    @org.springframework.beans.factory.annotation.Autowired
    SemanticEmbeddingInputFactory(
            StoryEmbeddingProperties properties, ArticleSummaryResolver summaryResolver) {
        this.properties = properties;
        this.summaryResolver = summaryResolver;
    }

    Input create(Article article) {
        String topics = article.aiEnrichment() == null
                ? "" : sorted(article.aiEnrichment().topics());
        String entities = article.aiEnrichment() == null ? "" : article.aiEnrichment().entities().stream()
                .map(this::entity)
                .sorted()
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
        ArticleSummaryResolver.ResolvedSummary resolved = summaryResolver.resolve(article);
        String semanticContent = """
                inputVersion: %s
                title: %s
                summary: %s
                topics: %s
                entities: %s
                category: %s
                """.formatted(
                properties.inputVersion(),
                normalize(article.title()),
                resolved == null ? "" : normalize(resolved.text()),
                topics,
                entities,
                article.category() == null ? "" : article.category().name()).trim();
        String text = SemanticSimilarityEmbeddingInput.format(semanticContent);
        text = truncate(text, properties.maxInputCharacters());
        return new Input(text, sha256(text), properties.inputVersion());
    }

    private String sorted(List<String> values) {
        return values.stream()
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
    }

    private String entity(ArticleEntity entity) {
        return normalize(entity.type()) + ":" + normalize(entity.name());
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncate(String value, int maximum) {
        if (value.length() <= maximum) {
            return value;
        }
        int end = maximum;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end).trim();
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record Input(String text, String hash, String version) {
    }
}
