/*
 * File overview: Represents a specific Ask Story Unavailable failure.
 * Area: story clustering, timelines, comparison, and Q&A.
 */
package lk.srilankannews.story.ask;

public class AskStoryUnavailableException extends RuntimeException {
    public AskStoryUnavailableException(String message) {
        super(message);
    }

    public AskStoryUnavailableException(Throwable cause) {
        super("Ask This Story is temporarily unavailable.", cause);
    }
}
