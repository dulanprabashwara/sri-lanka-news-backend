package lk.srilankannews.article.cache;

import java.util.Optional;
import lk.srilankannews.article.api.ArticleResponse;
import lk.srilankannews.common.api.PagedResponse;

public interface ArticleFeedCache {
    Lookup get(ArticleFeedQuery query);

    void put(
            ArticleFeedQuery query,
            String generation,
            PagedResponse<ArticleResponse> response);

    void invalidate();

    record Lookup(
            Optional<PagedResponse<ArticleResponse>> response,
            String generation,
            boolean available) {

        public static Lookup hit(
                PagedResponse<ArticleResponse> response, String generation) {
            return new Lookup(Optional.of(response), generation, true);
        }

        public static Lookup miss(String generation) {
            return new Lookup(Optional.empty(), generation, true);
        }

        public static Lookup unavailable() {
            return new Lookup(Optional.empty(), null, false);
        }
    }
}
