package lk.srilankannews.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lk.srilankannews.common.domain.Language;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/me/for-you")
public class ForYouController {
    private final ForYouService service;

    public ForYouController(ForYouService service) {
        this.service = service;
    }

    @GetMapping
    public ForYouFeedResponse feed(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Language displayLanguage) {
        return service.feed(jwt.getSubject(), page, size, displayLanguage);
    }
}
