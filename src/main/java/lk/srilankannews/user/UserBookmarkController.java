package lk.srilankannews.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lk.srilankannews.common.api.PagedResponse;
import lk.srilankannews.common.domain.Language;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/me/bookmarks")
public class UserBookmarkController {
    private final UserBookmarkService service;

    public UserBookmarkController(UserBookmarkService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResponse<BookmarkResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) BookmarkTargetType type,
            @RequestParam(required = false) Language displayLanguage) {
        return service.list(jwt.getSubject(), page, size, type, displayLanguage);
    }

    @PostMapping("/articles/{targetId}")
    public BookmarkStatusResponse bookmarkArticle(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String targetId) {
        return service.create(jwt.getSubject(), BookmarkTargetType.ARTICLE, targetId);
    }

    @GetMapping("/articles/{targetId}")
    public BookmarkStatusResponse articleStatus(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String targetId) {
        return service.status(jwt.getSubject(), BookmarkTargetType.ARTICLE, targetId);
    }

    @DeleteMapping("/articles/{targetId}")
    public ResponseEntity<Void> deleteArticle(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String targetId) {
        service.delete(jwt.getSubject(), BookmarkTargetType.ARTICLE, targetId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/stories/{targetId}")
    public BookmarkStatusResponse bookmarkStory(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String targetId) {
        return service.create(jwt.getSubject(), BookmarkTargetType.STORY, targetId);
    }

    @GetMapping("/stories/{targetId}")
    public BookmarkStatusResponse storyStatus(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String targetId) {
        return service.status(jwt.getSubject(), BookmarkTargetType.STORY, targetId);
    }

    @DeleteMapping("/stories/{targetId}")
    public ResponseEntity<Void> deleteStory(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String targetId) {
        service.delete(jwt.getSubject(), BookmarkTargetType.STORY, targetId);
        return ResponseEntity.noContent().build();
    }
}
