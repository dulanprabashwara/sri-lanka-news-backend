package lk.srilankannews.article.cache;

import static org.assertj.core.api.Assertions.assertThat;

import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class ArticleFeedQueryTest {

    @Test
    void effectiveDefaultsProduceOneDeterministicKey() {
        ArticleFeedQuery omittedDefaults =
                new ArticleFeedQuery(0, 20, null, null, null, Sort.Direction.DESC);
        ArticleFeedQuery explicitDefaults =
                new ArticleFeedQuery(0, 20, null, null, null, Sort.Direction.DESC);

        assertThat(omittedDefaults.cacheKey("7"))
                .isEqualTo(explicitDefaults.cacheKey("7"))
                .isEqualTo("news:feed:v7:page=0:size=20:source=all:"
                        + "category=all:language=all:display=original:sort=publishedAt,desc");
    }

    @Test
    void everySupportedQueryDimensionChangesTheKey() {
        ArticleFeedQuery baseline =
                new ArticleFeedQuery(0, 20, null, null, null, Sort.Direction.DESC);

        assertThat(new ArticleFeedQuery(
                1, 20, null, null, null, Sort.Direction.DESC).cacheKey("1"))
                .isNotEqualTo(baseline.cacheKey("1"));
        assertThat(new ArticleFeedQuery(
                0, 10, null, null, null, Sort.Direction.DESC).cacheKey("1"))
                .isNotEqualTo(baseline.cacheKey("1"));
        assertThat(new ArticleFeedQuery(
                0, 20, "newsfirst", null, null, Sort.Direction.DESC).cacheKey("1"))
                .isNotEqualTo(baseline.cacheKey("1"));
        assertThat(new ArticleFeedQuery(
                0, 20, null, ArticleCategory.POLITICS, null,
                Sort.Direction.DESC).cacheKey("1"))
                .isNotEqualTo(baseline.cacheKey("1"));
        assertThat(new ArticleFeedQuery(
                0, 20, null, null, Language.SI, Sort.Direction.DESC).cacheKey("1"))
                .isNotEqualTo(baseline.cacheKey("1"));
        assertThat(new ArticleFeedQuery(
                0, 20, null, null, null, Language.SI,
                Sort.Direction.DESC).cacheKey("1"))
                .isNotEqualTo(baseline.cacheKey("1"));
        assertThat(new ArticleFeedQuery(
                0, 20, null, null, null, Sort.Direction.ASC).cacheKey("1"))
                .isNotEqualTo(baseline.cacheKey("1"));
    }
}
