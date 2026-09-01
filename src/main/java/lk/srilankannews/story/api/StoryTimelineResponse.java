package lk.srilankannews.story.api;

import java.time.Instant;
import java.util.List;

public record StoryTimelineResponse(
        String storyId,
        String canonicalTitle,
        Instant firstPublishedAt,
        Instant lastPublishedAt,
        int eventCount,
        int sourceCount,
        List<TimelineEventResponse> events
) {
    public StoryTimelineResponse {
        events = List.copyOf(events);
    }
}
