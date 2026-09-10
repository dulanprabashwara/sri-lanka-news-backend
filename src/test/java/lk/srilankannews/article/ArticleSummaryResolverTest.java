package lk.srilankannews.article;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;

class ArticleSummaryResolverTest {
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private final ArticleSummaryResolver resolver = new ArticleSummaryResolver();

    @Test
    void publisherSummaryHasHighestPriorityAndIsNotOverwritten() {
        Article article = article(
                "Publisher summary", "AI summary",
                "Body sentence with enough meaningful reporting to become a fallback summary.");

        ArticleSummaryResolver.ResolvedSummary result = resolver.resolve(article);

        assertThat(result.text()).isEqualTo("Publisher summary");
        assertThat(result.source()).isEqualTo(ArticleSummaryResolver.SummarySource.PUBLISHER);
        assertThat(article.summary()).isEqualTo("Publisher summary");
    }

    @Test
    void aiSummaryIsUsedWhenPublisherSummaryIsMissing() {
        ArticleSummaryResolver.ResolvedSummary result = resolver.resolve(article(
                null, "AI summary",
                "Body sentence with enough meaningful reporting to become a fallback summary."));

        assertThat(result.text()).isEqualTo("AI summary");
        assertThat(result.source()).isEqualTo(ArticleSummaryResolver.SummarySource.AI);
    }

    @Test
    void articleBodyProvidesExtractiveSummaryWhenOtherSourcesAreMissing() {
        ArticleSummaryResolver.ResolvedSummary result = resolver.resolve(article(
                null, null,
                "The first meaningful sentence explains the central event clearly for readers. "
                        + "The second meaningful sentence adds useful context without new claims. "
                        + "A third sentence must not be included."));

        assertThat(result.source()).isEqualTo(ArticleSummaryResolver.SummarySource.EXTRACTIVE);
        assertThat(result.text())
                .contains("central event")
                .contains("useful context")
                .doesNotContain("third sentence");
        assertThat(result.text().length()).isLessThanOrEqualTo(400);
    }

    @Test
    void junkBylineDuplicateTitleAndContinueReadingAreRemoved() {
        ArticleSummaryResolver.ResolvedSummary result = resolver.resolve(article(
                null, null,
                "Headline\nBy Reporter One\nContinue Reading\nAdvertisement\n"
                        + "Officials confirmed the decision after a detailed public meeting. "
                        + "Officials confirmed the decision after a detailed public meeting."));

        assertThat(result.text())
                .isEqualTo("Officials confirmed the decision after a detailed public meeting.")
                .doesNotContainIgnoringCase("continue reading")
                .doesNotContain("Reporter One");
    }

    @Test
    void noUsableBodyReturnsNullSafely() {
        assertThat(resolver.resolve(article(
                " ", null, "Headline\nBy Reporter One\nContinue Reading\nAdvertisement")))
                .isNull();
    }

    private Article article(String publisherSummary, String aiSummary, String content) {
        ArticleAiEnrichment enrichment = aiSummary == null
                ? null
                : new ArticleAiEnrichment(
                        aiSummary, List.of(), List.of(), List.of(), "gemini", "v1", NOW);
        return new Article(
                "article-1", "source-1", "Headline",
                "https://example.com/1", "https://example.com/1", Language.EN,
                List.of(), NOW, NOW, ArticleCategory.LOCAL, content, publisherSummary,
                "content-hash", enrichment, null, Map.of(), null,
                ProcessingStatus.PENDING, null, NOW, NOW);
    }
}
