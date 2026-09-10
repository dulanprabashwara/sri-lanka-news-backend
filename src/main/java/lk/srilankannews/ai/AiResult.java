package lk.srilankannews.ai;

import java.util.List;
import lk.srilankannews.article.ArticleCategory;

public record AiResult(
        String summary,
        ArticleCategory category,
        List<String> topics,
        List<String> keywords,
        List<AiEntity> entities,
        String provider,
        String model) {

    public AiResult(
            String summary,
            ArticleCategory category,
            List<String> topics,
            List<String> keywords,
            List<AiEntity> entities) {
        this(summary, category, topics, keywords, entities, "GEMINI", null);
    }
}
