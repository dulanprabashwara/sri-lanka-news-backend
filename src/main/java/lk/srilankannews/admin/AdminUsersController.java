package lk.srilankannews.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("@adminAuthorization.isAdmin(authentication)")
public class AdminUsersController {

    private final AdminMongoOperations operations;

    public AdminUsersController(AdminMongoOperations operations) {
        this.operations = operations;
    }

    @GetMapping("/summary")
    public AdminOverviewResponse.UserCounts summary() {
        return new AdminOverviewResponse.UserCounts(
                operations.userPreferencesCount(),
                operations.totalBookmarks(),
                operations.totalFollows()
        );
    }
}
