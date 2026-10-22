/*
 * File overview: Defines data returned by Follow Batch Status API operations.
 * Area: reader preferences and personalization.
 */
package lk.srilankannews.user;

import java.util.List;

public record FollowBatchStatusResponse(
        List<SourceFollowStatusResponse> sources,
        List<TopicFollowStatusResponse> topics
) {
}
