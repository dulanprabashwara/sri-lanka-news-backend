package lk.srilankannews.story.ask;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;

public record GroundedAnswerInput(
        String question,
        Language answerLanguage,
        String storyTitle,
        ArticleCategory storyCategory,
        Instant firstPublishedAt,
        Instant lastPublishedAt,
        String storyInventory,
        List<GroundedSourceContext> sources) {
    public GroundedAnswerInput {
        sources = List.copyOf(sources);
    }
}
