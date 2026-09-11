package lk.srilankannews.article;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ArticleQueryRepository {

    Page<Article> findAll(ArticleFilter filter, Pageable pageable);

    List<Article> findTrendingCandidates(Instant publishedSince, ArticleCategory category, int limit);

    List<Article> findRecentStoryArticles(Set<String> storyIds, Instant publishedSince);

    java.util.Map<String, Long> countNewArticlesBySource(java.util.Map<String, Instant> sourceBaselines);
}
