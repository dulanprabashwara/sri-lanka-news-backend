package lk.srilankannews.article.api;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        String summary,
        List<String> topics,
        SourceSummaryResponse source,
        @JsonInclude(JsonInclude.Include.NON_NULL) ArticleLeadMediaResponse leadMedia,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalizedContentResponse localizedContent
) {
    public ArticleResponse {
        authors = List.copyOf(authors);
        topics = List.copyOf(topics);
    }

    public ArticleResponse(
            String id, String title, String originalUrl, Language originalLanguage,
            List<String> authors, Instant publishedAt, Instant discoveredAt,
            ArticleCategory category, String summary, List<String> topics,
            SourceSummaryResponse source) {
        this(id, title, originalUrl, originalLanguage, authors, publishedAt,
                discoveredAt, category, summary, topics, source, null, null);
    }

    public ArticleResponse(
            String id, String title, String originalUrl, Language originalLanguage,
            List<String> authors, Instant publishedAt, Instant discoveredAt,
            ArticleCategory category, SourceSummaryResponse source) {
        this(id, title, originalUrl, originalLanguage, authors, publishedAt,
                discoveredAt, category, null, List.of(), source, null, null);
    }
}
