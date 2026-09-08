package lk.srilankannews.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.common.validation.HttpUrl;

public record CreateArticleCommand(
        @NotBlank String sourceId,
        @NotBlank @Size(max = 500) String title,
        @NotBlank @HttpUrl @Size(max = 2048) String originalUrl,
        @NotBlank @HttpUrl @Size(max = 2048) String canonicalUrl,
        @NotNull Language originalLanguage,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 200) String> authors,
        @NotNull @PastOrPresent Instant publishedAt,
        @NotNull @PastOrPresent Instant discoveredAt,
        ArticleCategory category,
        @NotBlank @Size(max = 500_000) String extractedContent,
        @Size(max = 2000) String summary,
        ArticleLeadMedia leadMedia
) {
    public CreateArticleCommand {
        authors = authors == null ? null : List.copyOf(authors);
    }

    public CreateArticleCommand(
            String sourceId, String title, String originalUrl, String canonicalUrl,
            Language originalLanguage, List<String> authors, Instant publishedAt,
            Instant discoveredAt, ArticleCategory category, String extractedContent,
            String summary) {
        this(sourceId, title, originalUrl, canonicalUrl, originalLanguage, authors, publishedAt, discoveredAt, category, extractedContent, summary, null);
    }
}
