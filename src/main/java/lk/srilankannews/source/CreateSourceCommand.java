package lk.srilankannews.source;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.common.validation.HttpUrl;

public record CreateSourceCommand(
        @NotBlank @Size(max = 150) String name,
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "must be a lowercase URL-safe slug")
        String slug,
        @NotBlank @HttpUrl @Size(max = 2048) String baseUrl,
        @NotNull Language defaultLanguage,
        @NotNull IngestionType ingestionType,
        boolean enabled
) {
}
