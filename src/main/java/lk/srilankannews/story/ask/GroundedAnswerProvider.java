package lk.srilankannews.story.ask;

public interface GroundedAnswerProvider {
    GroundedAnswerResult answer(GroundedAnswerInput input);

    default boolean hasFallback() {
        return false;
    }
}
