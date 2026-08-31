package lk.srilankannews.article.cache;

import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.api.PagedResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "news.cache.article-feed.enabled", havingValue = "false")
public class NoOpArticleFeedCache implements ArticleFeedCache {
    @Override
    public Lookup get(ArticleFeedQuery query) {
        return Lookup.unavailable();
    }

    @Override
    public void put(
            ArticleFeedQuery query,
            String generation,
            PagedResponse<ArticleResponse> response) {
    }

    @Override
    public void invalidate() {
    }
}
