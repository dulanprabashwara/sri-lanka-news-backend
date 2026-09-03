package lk.srilankannews.ingestion.run.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CompleteRequest(
        @NotNull @Min(0) Integer articlesDiscovered,
        @NotNull @Min(0) Integer articlesSubmitted,
        @NotNull @Min(0) Integer articlesSucceeded,
        @NotNull @Min(0) Integer articlesFailed
) {
}
