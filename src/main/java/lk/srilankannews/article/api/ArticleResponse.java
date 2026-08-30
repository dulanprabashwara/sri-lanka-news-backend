package lk.srilankannews.article.api;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.api.SourceSummaryResponse;

public record ArticleResponse(
        String id,
        String title,
        String originalUrl,
        Language originalLanguage,
        List<String> authors,
        Instant publishedAt,
        Instant discoveredAt,
        ArticleCategory category,
        SourceSummaryResponse source
) {
    public ArticleResponse {
        authors = List.copyOf(authors);
    }
}
