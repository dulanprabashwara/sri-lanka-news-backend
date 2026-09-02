package lk.srilankannews.article.search;

import java.util.List;
import lk.srilankannews.article.api.ArticleResponse;
import org.springframework.data.domain.Page;

public record TextSearchResponse(
        String query,
        List<ArticleResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {
    public TextSearchResponse {
        content = List.copyOf(content);
    }

    static TextSearchResponse from(String query, Page<ArticleResponse> page) {
        return new TextSearchResponse(query, page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
