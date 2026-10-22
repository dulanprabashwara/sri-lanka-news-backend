/*
 * File overview: Defines data returned by Ask Story API operations.
 * Area: story clustering, timelines, comparison, and Q&A.
 */
package lk.srilankannews.story.ask;

import java.util.List;

public record AskStoryResponse(
        String storyId,
        boolean answerable,
        String answer,
        List<AskStoryCitationResponse> citations) {
    public AskStoryResponse {
        citations = List.copyOf(citations);
    }
}
