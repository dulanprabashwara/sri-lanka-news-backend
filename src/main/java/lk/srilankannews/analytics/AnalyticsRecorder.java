package lk.srilankannews.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsRecorder {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsRecorder.class);
    
    private final AnalyticsEventService eventService;

    public AnalyticsRecorder(AnalyticsEventService eventService) {
        this.eventService = eventService;
    }

    public void recordBestEffort(AnalyticsEventType type, String eventId, String articleId, String storyId, String sourceId, Integer resultCount, String searchMode, String category) {
        try {
            eventService.recordServerEvent(type, eventId, articleId, storyId, sourceId, resultCount, searchMode, category);
        } catch (Exception e) {
            log.warn("Failed to record analytics event: {}", type, e);
        }
    }
}
