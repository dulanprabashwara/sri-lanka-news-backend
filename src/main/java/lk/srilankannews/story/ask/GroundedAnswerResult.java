package lk.srilankannews.story.ask;

import java.util.List;

public record GroundedAnswerResult(
        boolean answerable,
        String answer,
        List<String> citedSourceIds) {
    public GroundedAnswerResult {
        citedSourceIds = citedSourceIds == null ? List.of() : List.copyOf(citedSourceIds);
    }
}
