package lk.srilankannews.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lk.srilankannews.common.api.PagedResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/me/follows")
public class UserFollowController {
    private final UserFollowService service;

    public UserFollowController(UserFollowService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<FollowResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) FollowTargetType type) {
        return service.list(jwt.getSubject(), page, size, type);
    }

    @PostMapping("/sources/{slug}")
    public FollowStatusResponse followSource(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(min = 1, max = 100) String slug) {
        return service.followSource(jwt.getSubject(), slug);
    }

    @GetMapping("/sources/{slug}")
    public FollowStatusResponse sourceStatus(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(min = 1, max = 100) String slug) {
        return service.sourceStatus(jwt.getSubject(), slug);
    }

    @DeleteMapping("/sources/{slug}")
    public ResponseEntity<Void> unfollowSource(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(min = 1, max = 100) String slug) {
        service.unfollowSource(jwt.getSubject(), slug);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sources/{slug}/seen")
    public ResponseEntity<Void> markSourceSeen(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(min = 1, max = 100) String slug) {
        service.markSourceSeen(jwt.getSubject(), slug);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/topics")
    public FollowStatusResponse followTopic(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TopicFollowRequest request) {
        return service.followTopic(jwt.getSubject(), request.topic());
    }

    @GetMapping("/topics")
    public FollowStatusResponse topicStatus(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @NotBlank @Size(max = 120) String topic) {
        return service.topicStatus(jwt.getSubject(), topic);
    }

    @DeleteMapping("/topics")
    public ResponseEntity<Void> unfollowTopic(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @NotBlank @Size(max = 120) String topic) {
        service.unfollowTopic(jwt.getSubject(), topic);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/status")
    public FollowBatchStatusResponse batchStatus(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FollowBatchStatusRequest request) {
        return service.batchStatus(jwt.getSubject(), request);
    }
}
