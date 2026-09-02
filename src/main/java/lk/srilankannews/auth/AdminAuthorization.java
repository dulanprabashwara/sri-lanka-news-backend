package lk.srilankannews.auth;

import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("adminAuthorization")
public class AdminAuthorization {

    private final Set<String> adminUserIds;

    public AdminAuthorization(AdminProperties properties) {
        this.adminUserIds = properties.parsedUserIds();
    }

    public boolean isAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        String subject = jwt.getSubject();
        return subject != null && adminUserIds.contains(subject);
    }
}
