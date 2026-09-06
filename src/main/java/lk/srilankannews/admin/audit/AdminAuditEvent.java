package lk.srilankannews.admin.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "admin_audit_events")
public record AdminAuditEvent(
        @Id String id,
        String adminUserId,
        String eventType,
        String targetSourceId,
        Object metadata,
        @Indexed(direction = org.springframework.data.mongodb.core.index.IndexDirection.DESCENDING) Instant createdAt,
        @JsonIgnore Instant expiresAt
) {
}
