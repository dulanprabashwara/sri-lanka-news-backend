package lk.srilankannews.admin.audit;

import java.time.Clock;
import java.time.Instant;
import lk.srilankannews.retention.RetentionPolicyService;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditEventService {
    private final AdminAuditEventRepository repository;
    private final RetentionPolicyService retentionPolicyService;
    private final Clock clock;

    public AdminAuditEventService(AdminAuditEventRepository repository,
                                 RetentionPolicyService retentionPolicyService,
                                 Clock clock) {
        this.repository = repository;
        this.retentionPolicyService = retentionPolicyService;
        this.clock = clock;
    }

    public void logEvent(String adminUserId, String eventType, String targetSourceId, Object metadata) {
        Instant now = Instant.now(clock);
        Instant expiresAt = retentionPolicyService.calculateAdminAuditExpiry(now).orElse(null);
        repository.save(new AdminAuditEvent(
                null, adminUserId, eventType, targetSourceId, metadata, now, expiresAt
        ));
    }
}
