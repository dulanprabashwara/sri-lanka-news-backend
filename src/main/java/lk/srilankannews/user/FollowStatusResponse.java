package lk.srilankannews.user;

import java.time.Instant;

public record FollowStatusResponse(boolean followed, Instant followedAt) {
    static FollowStatusResponse absent() {
        return new FollowStatusResponse(false, null);
    }
}
