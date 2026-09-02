package lk.srilankannews.story.ask;

public interface GroundedAnswerProvider {
    GroundedAnswerResult answer(GroundedAnswerInput input);
}
