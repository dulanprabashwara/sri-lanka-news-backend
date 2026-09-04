package lk.srilankannews.article.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lk.srilankannews.article.MediaType;
import lk.srilankannews.common.validation.HttpUrl;

public record LeadMediaInput(
        @NotBlank @HttpUrl @Size(max = 2048) String url,
        @NotNull MediaType type,
        @Size(max = 500) String altText,
        @Size(max = 500) String caption,
        @Size(max = 200) String credit,
        @Positive Integer width,
        @Positive Integer height,
        @Size(max = 100) String discoveredFrom
) {
}
