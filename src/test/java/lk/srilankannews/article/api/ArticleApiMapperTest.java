package lk.srilankannews.article.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.api.SourceApiMapper;
import org.junit.jupiter.api.Test;

class ArticleApiMapperTest {

    private final ArticleApiMapper mapper = new ArticleApiMapper(new SourceApiMapper());

    @Test
    void mapsPublicArticleAndNestedSourceFields() {
        Instant publishedAt = Instant.parse("2026-08-30T09:00:00Z");
        Article article = new Article(
                "507f1f77bcf86cd799439011",
                "source-1",
                "Headline",
                "https://example.com/original",
                "https://example.com/canonical",
                Language.EN,
                List.of("Reporter One"),
                publishedAt,
                publishedAt.plusSeconds(60),
                ArticleCategory.LOCAL,
                "Internal fixture content",
                publishedAt,
                publishedAt);
        Source source = new Source(
                "source-1",
                "Daily Mirror",
                "daily-mirror",
                "https://www.dailymirror.lk",
                Language.EN,
                IngestionType.RSS,
                true,
                publishedAt,
                publishedAt);

        ArticleResponse response = mapper.toResponse(article, source);

        assertThat(response.id()).isEqualTo(article.id());
        assertThat(response.originalUrl()).isEqualTo(article.originalUrl());
        assertThat(response.source().name()).isEqualTo(source.name());
        assertThat(response.source().slug()).isEqualTo(source.slug());
        assertThat(ArticleResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("extractedContent");
    }
}
