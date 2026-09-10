package lk.srilankannews.processing.enrichment;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "article_enrichment_jobs")
@CompoundIndex(
        name = "idx_enrichment_jobs_due",
        def = "{'status': 1, 'nextRetryAt': 1, 'leaseUntil': 1}")
public record ArticleEnrichmentJob(
        @Id String articleId,
        EnrichmentStatus status,
        Integer attempts,
        Instant nextRetryAt,
        Instant leaseUntil,
        String claimToken,
        String lastFailureKind,
        Instant updatedAt) {

    public ArticleEnrichmentJob {
        if (attempts == null) {
            attempts = 0;
        }
        if (status == null) {
            status = EnrichmentStatus.PENDING;
        }
    }
}
