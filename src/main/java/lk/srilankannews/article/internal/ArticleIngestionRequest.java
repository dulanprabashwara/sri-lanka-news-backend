package lk.srilankannews.article.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.common.validation.HttpUrl;
import jakarta.validation.Valid;

public record ArticleIngestionRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "must be a lowercase URL-safe slug")
        String sourceSlug,
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
        @Valid LeadMediaInput leadMedia
) {
    public ArticleIngestionRequest {
        authors = authors == null ? null : List.copyOf(authors);
    }
}
