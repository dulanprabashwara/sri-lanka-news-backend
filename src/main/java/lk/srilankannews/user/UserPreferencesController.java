package lk.srilankannews.user;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/preferences")
public class UserPreferencesController {
    private final UserPreferencesService service;

    public UserPreferencesController(UserPreferencesService service) {
        this.service = service;
    }

    @GetMapping
    public UserPreferencesResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(jwt.getSubject());
    }

    @PutMapping
    public UserPreferencesResponse update(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UserPreferencesRequest request) {
        return service.update(jwt.getSubject(), request);
    }
}
