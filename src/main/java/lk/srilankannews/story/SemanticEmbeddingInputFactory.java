package lk.srilankannews.story;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleEntity;
import org.springframework.stereotype.Component;

@Component
class SemanticEmbeddingInputFactory {
    private static final String SEMANTIC_SIMILARITY_INSTRUCTION =
            "task: sentence similarity | query: ";

    private final StoryEmbeddingProperties properties;

    SemanticEmbeddingInputFactory(StoryEmbeddingProperties properties) {
        this.properties = properties;
    }

    Input create(Article article) {
        if (article.aiEnrichment() == null) {
            throw new IllegalStateException("Article must be enriched before embedding");
        }
        String topics = sorted(article.aiEnrichment().topics());
        String entities = article.aiEnrichment().entities().stream()
                .map(this::entity)
                .sorted()
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
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
                normalize(article.aiEnrichment().summary()),
                topics,
                entities,
                article.category() == null ? "" : article.category().name()).trim();
        String text = SEMANTIC_SIMILARITY_INSTRUCTION + semanticContent;
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
