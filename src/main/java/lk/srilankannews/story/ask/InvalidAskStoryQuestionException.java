/*
 * File overview: Represents a specific Invalid Ask Story Question failure.
 * Area: story clustering, timelines, comparison, and Q&A.
 */
package lk.srilankannews.story.ask;

public class InvalidAskStoryQuestionException extends RuntimeException {
    public InvalidAskStoryQuestionException(String message) {
        super(message);
    }
}
