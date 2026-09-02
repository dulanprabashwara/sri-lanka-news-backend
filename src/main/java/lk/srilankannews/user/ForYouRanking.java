package lk.srilankannews.user;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.source.Source;
import org.springframework.stereotype.Component;

@Component
class ForYouRanking {
    static final int FOLLOWED_SOURCE_WEIGHT = 40;
    static final int FOLLOWED_TOPIC_WEIGHT = 30;
    static final int MAX_TOPIC_WEIGHT = 60;
    static final int PREFERRED_CATEGORY_WEIGHT = 20;

    private final TopicNormalizer topicNormalizer;

    ForYouRanking(TopicNormalizer topicNormalizer) {
        this.topicNormalizer = topicNormalizer;
    }

    RankedArticle rank(Article article, Source source, Set<String> followedSourceIds,
            Map<String, String> followedTopics, Set<ArticleCategory> preferredCategories) {
        int score = 0;
        List<RecommendationReasonResponse> reasons = new ArrayList<>();
        if (followedSourceIds.contains(article.sourceId())) {
            score += FOLLOWED_SOURCE_WEIGHT;
            reasons.add(new RecommendationReasonResponse(
                    RecommendationReasonType.FOLLOWED_SOURCE, source.name()));
        }

        Set<String> matchedTopics = new HashSet<>();
        if (article.aiEnrichment() != null) {
            for (String topic : article.aiEnrichment().topics()) {
                TopicNormalizer.NormalizedTopic normalized = topicNormalizer.normalize(topic);
                if (followedTopics.containsKey(normalized.key())
                        && matchedTopics.add(normalized.key())
                        && matchedTopics.size() <= MAX_TOPIC_WEIGHT / FOLLOWED_TOPIC_WEIGHT) {
                    score += FOLLOWED_TOPIC_WEIGHT;
                    reasons.add(new RecommendationReasonResponse(
                            RecommendationReasonType.FOLLOWED_TOPIC, normalized.label()));
                }
            }
        }
        if (article.category() != null && preferredCategories.contains(article.category())) {
            score += PREFERRED_CATEGORY_WEIGHT;
            reasons.add(new RecommendationReasonResponse(
                    RecommendationReasonType.PREFERRED_CATEGORY, article.category().name()));
        }
        return new RankedArticle(article, source, score, reasons);
    }

    record RankedArticle(
            Article article, Source source, int score,
            List<RecommendationReasonResponse> reasons) {
        RankedArticle {
            reasons = List.copyOf(reasons);
        }
    }
}
