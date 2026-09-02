package lk.srilankannews.article.search;

import java.util.List;
import lk.srilankannews.article.api.ArticleResponse;

public record SemanticSearchResponse(
        String query,
        List<ArticleResponse> content,
        int page,
        int size,
        boolean hasMore,
        boolean first) {
    public SemanticSearchResponse {
        content = List.copyOf(content);
    }

    static SemanticSearchResponse empty(String query, int page, int size) {
        return new SemanticSearchResponse(query, List.of(), page, size, false, page == 0);
    }
}
