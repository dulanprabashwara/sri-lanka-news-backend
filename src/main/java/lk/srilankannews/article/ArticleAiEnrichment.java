package lk.srilankannews.article;

import java.time.Instant;
import java.util.List;

public record ArticleAiEnrichment(
        String summary,
        List<String> topics,
        List<String> keywords,
        List<ArticleEntity> entities,
        String model,
        String promptVersion,
        Instant processedAt) {
    public ArticleAiEnrichment {
        topics = List.copyOf(topics);
        keywords = List.copyOf(keywords);
        entities = List.copyOf(entities);
    }

    public boolean matches(String expectedModel, String expectedPromptVersion) {
        return model.equals(expectedModel) && promptVersion.equals(expectedPromptVersion);
    }
}
