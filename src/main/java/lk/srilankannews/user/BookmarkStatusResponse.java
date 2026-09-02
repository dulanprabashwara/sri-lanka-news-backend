package lk.srilankannews.user;

import java.time.Instant;

public record BookmarkStatusResponse(boolean bookmarked, Instant createdAt) {
}
