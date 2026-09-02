package lk.srilankannews.user;

import java.time.Instant;

public record SourceFollowStatusResponse(String slug, boolean followed, Instant followedAt) {
}
