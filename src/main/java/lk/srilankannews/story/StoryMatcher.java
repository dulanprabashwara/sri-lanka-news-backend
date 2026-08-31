package lk.srilankannews.story;

import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleEntity;
import org.springframework.stereotype.Component;

@Component
class StoryMatcher {
    static final String MATCHING_VERSION = "lexical-v1";
    private static final double MINIMUM_TITLE_OVERLAP = 0.45;

    private final StoryTextNormalizer normalizer;

    StoryMatcher(StoryTextNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    double score(Article article, Article representative) {
        if (article.originalLanguage() != representative.originalLanguage()) {
            return 0;
        }
        if (article.category() != null && representative.category() != null
                && article.category() != representative.category()) {
            return 0;
        }

        double title = jaccard(
                normalizer.tokens(article.title()), normalizer.tokens(representative.title()));
        if (title < MINIMUM_TITLE_OVERLAP) {
            return 0;
        }

        double topics = jaccard(
                normalizer.values(article.aiEnrichment().topics()),
                normalizer.values(representative.aiEnrichment().topics()));
        double entities = jaccard(
                normalizer.values(article.aiEnrichment().entities().stream()
                        .map(ArticleEntity::name).toList()),
                normalizer.values(representative.aiEnrichment().entities().stream()
                        .map(ArticleEntity::name).toList()));
        double category = article.category() != null
                && article.category() == representative.category() ? 1 : 0;

        return (0.70 * title) + (0.15 * topics) + (0.10 * entities) + (0.05 * category);
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        long intersection = left.stream().filter(right::contains).count();
        int union = left.size() + right.size() - (int) intersection;
        return union == 0 ? 0 : (double) intersection / union;
    }
}
