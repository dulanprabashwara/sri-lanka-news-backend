package lk.srilankannews.admin.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("@adminAuthorization.isAdmin(authentication)")
public class AdminAuditController {

    private final AdminAuditEventRepository repository;

    public AdminAuditController(AdminAuditEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Page<AdminAuditEvent> auditEvents(
            @PageableDefault(sort = "timestamp", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return repository.findAll(pageable);
    }
}
