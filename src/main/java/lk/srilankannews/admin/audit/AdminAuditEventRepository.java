package lk.srilankannews.admin.audit;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminAuditEventRepository extends MongoRepository<AdminAuditEvent, String> {
}
