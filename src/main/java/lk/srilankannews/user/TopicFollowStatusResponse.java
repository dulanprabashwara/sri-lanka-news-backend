package lk.srilankannews.user;

import java.time.Instant;

public record TopicFollowStatusResponse(String topic, boolean followed, Instant followedAt) {
}
