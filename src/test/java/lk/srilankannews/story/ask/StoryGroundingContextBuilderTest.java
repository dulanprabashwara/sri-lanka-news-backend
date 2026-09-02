package lk.srilankannews.story.ask;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.story.Story;
import org.junit.jupiter.api.Test;

class StoryGroundingContextBuilderTest {
    private static final Instant NOW = Instant.parse("2026-08-30T08:00:00Z");

    @Test
    void boundsPrivateExtractedContentAndTotalContextDeterministically() {
        AskStoryProperties properties = new AskStoryProperties(
                "ask-story-v1", 500, 5, 500, 1000, 3000);
        StoryGroundingContextBuilder builder = new StoryGroundingContextBuilder(properties);
        Article article = new Article(
                "a1", "s1", "Title", "https://example.com/a1", "https://example.com/a1",
                Language.EN, List.of(), NOW, NOW, ArticleCategory.LOCAL, "x".repeat(5000),
                "hash", null, null, Map.of(), ProcessingStatus.COMPLETED, "story", NOW, NOW);
        Source source = new Source("s1", "Publisher", "publisher", "https://example.com",
                Language.EN, IngestionType.RSS, true, NOW, NOW);
        Story story = new Story("story", "Title", "a1", ArticleCategory.LOCAL, NOW, NOW,
                1, Set.of("s1"), Set.of("a1"), NOW, NOW, "hybrid-v1");

        StoryGroundingContextBuilder.BuiltContext context = builder.build(
                story, List.of(article), List.of(article), Map.of("s1", source));

        assertThat(context.sources()).singleElement().satisfies(item ->
                assertThat(item.content().length()).isLessThanOrEqualTo(500));
        int dataLength = context.inventory().length() + context.sources().stream()
                .mapToInt(item -> item.title().length() + item.summary().length() + item.content().length())
                .sum();
        assertThat(dataLength).isLessThanOrEqualTo(1000);
    }
}
