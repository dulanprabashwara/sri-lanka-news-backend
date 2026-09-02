package lk.srilankannews.user;

import java.util.List;

public record FollowBatchStatusResponse(
        List<SourceFollowStatusResponse> sources,
        List<TopicFollowStatusResponse> topics
) {
}
