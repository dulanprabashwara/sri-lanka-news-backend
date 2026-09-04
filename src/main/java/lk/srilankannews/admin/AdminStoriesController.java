package lk.srilankannews.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stories")
@PreAuthorize("@adminAuthorization.isAdmin(authentication)")
public class AdminStoriesController {

    private final AdminMongoOperations operations;

    public AdminStoriesController(AdminMongoOperations operations) {
        this.operations = operations;
    }

    @GetMapping
    public Page<AdminStoryResponse> stories(
            @PageableDefault(sort = "lastPublishedAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return operations.findStoriesPage(pageable).map(AdminStoryResponse::from);
    }
}
