package lk.srilankannews.user;

import java.time.Instant;
import lk.srilankannews.source.api.SourceSummaryResponse;

public record FollowResponse(
        String followId,
        FollowTargetType targetType,
        Instant createdAt,
        SourceSummaryResponse source,
        FollowTopicResponse topic
) {
}
