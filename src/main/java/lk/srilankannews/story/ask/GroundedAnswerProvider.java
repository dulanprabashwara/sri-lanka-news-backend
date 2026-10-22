/*
 * File overview: Defines the provider contract for Grounded Answer.
 * Area: story clustering, timelines, comparison, and Q&A.
 */
package lk.srilankannews.story.ask;

public interface GroundedAnswerProvider {
    GroundedAnswerResult answer(GroundedAnswerInput input);

    default boolean hasFallback() {
        return false;
    }
}
