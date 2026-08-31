package lk.srilankannews.story;

import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleSemanticEmbedding;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
class StorySemanticMatcher {
    private static final double LEXICAL_WEIGHT = 0.65;
    private static final double SEMANTIC_WEIGHT = 0.35;

    private final StoryEmbeddingProperties properties;

    StorySemanticMatcher(StoryEmbeddingProperties properties) {
        this.properties = properties;
    }

    double score(Article article, Article representative, double lexicalScore) {
        if (article.category() != null && representative.category() != null
                && article.category() != representative.category()) {
            return 0;
        }

        ArticleSemanticEmbedding left = article.semanticEmbedding();
        ArticleSemanticEmbedding right = representative.semanticEmbedding();
        if (!compatible(left, right)) {
            return article.originalLanguage() == representative.originalLanguage()
                    ? lexicalScore : 0;
        }

        double semantic = CosineSimilarity.calculate(left.values(), right.values());
        if (article.originalLanguage() != representative.originalLanguage()) {
            return semantic >= properties.crossLanguageSemanticThreshold() ? semantic : 0;
        }
        if (lexicalScore >= 1.0 || semantic < properties.semanticThreshold()) {
            return lexicalScore;
        }
        return Math.max(
                lexicalScore,
                (LEXICAL_WEIGHT * lexicalScore) + (SEMANTIC_WEIGHT * semantic));
    }

    private boolean compatible(
            ArticleSemanticEmbedding left,
            ArticleSemanticEmbedding right) {
        return left != null
                && right != null
                && Objects.equals(properties.model(), left.model())
                && Objects.equals(properties.model(), right.model())
                && properties.dimensions() == left.dimensions()
                && properties.dimensions() == right.dimensions()
                && Objects.equals(properties.inputVersion(), left.inputVersion())
                && Objects.equals(properties.inputVersion(), right.inputVersion())
                && Objects.equals(left.model(), right.model())
                && left.dimensions() == right.dimensions()
                && Objects.equals(left.inputVersion(), right.inputVersion())
                && left.values().size() == right.values().size();
    }
}
