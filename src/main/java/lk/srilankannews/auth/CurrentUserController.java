package lk.srilankannews.auth;

import lk.srilankannews.user.UserPreferencesService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class CurrentUserController {

    private final UserPreferencesService userPreferencesService;

    public CurrentUserController(UserPreferencesService userPreferencesService) {
        this.userPreferencesService = userPreferencesService;
    }

    @GetMapping
    public CurrentUserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        userPreferencesService.get(jwt.getSubject());
        return new CurrentUserResponse(true, jwt.getSubject(), jwt.getClaimAsString("email"));
    }
}

