package lk.srilankannews.admin.audit;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditEventService {
    private final AdminAuditEventRepository repository;
    private final Clock clock;

    public AdminAuditEventService(AdminAuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void logEvent(String adminUserId, String eventType, String targetSourceId, Object metadata) {
        repository.save(new AdminAuditEvent(
                null, adminUserId, eventType, targetSourceId, metadata, Instant.now(clock)
        ));
    }
}
