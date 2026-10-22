/*
 * File overview: Defines the MongoDB document stored for Ingestion Source Lease records.
 * Area: ingestion coordination and run tracking.
 */
package lk.srilankannews.ingestion.run;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ingestion_source_leases")
public record IngestionSourceLease(
        @Id String sourceId,
        String runId,
        String workerId,
        Instant expiresAt
) {
}
