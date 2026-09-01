package lk.srilankannews.story.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record StoryTimelineResponse(
        String storyId,
        String canonicalTitle,
        Instant firstPublishedAt,
        Instant lastPublishedAt,
        int eventCount,
        int sourceCount,
        List<TimelineEventResponse> events,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        LocalizedStoryContentResponse localizedContent
) {
    public StoryTimelineResponse {
        events = List.copyOf(events);
    }

    public StoryTimelineResponse(
            String storyId, String canonicalTitle, Instant firstPublishedAt,
            Instant lastPublishedAt, int eventCount, int sourceCount,
            List<TimelineEventResponse> events) {
        this(storyId, canonicalTitle, firstPublishedAt, lastPublishedAt,
                eventCount, sourceCount, events, null);
    }
}
