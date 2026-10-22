/*
 * File overview: Defines MongoDB persistence and query operations for Admin Audit Event.
 * Area: administrative operations and monitoring.
 */
package lk.srilankannews.admin.audit;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminAuditEventRepository extends MongoRepository<AdminAuditEvent, String> {
}
