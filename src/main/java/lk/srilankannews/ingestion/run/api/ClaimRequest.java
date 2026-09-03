package lk.srilankannews.ingestion.run.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lk.srilankannews.ingestion.run.IngestionTriggerType;

public record ClaimRequest(
        @NotBlank String sourceSlug,
        @NotNull IngestionTriggerType triggerType,
        @NotNull Instant scheduledFor,
        @NotBlank String workerId
) {
}
