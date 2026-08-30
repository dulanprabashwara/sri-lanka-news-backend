package lk.srilankannews.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.common.domain.Language;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class ArticleQueryRepositoryImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void buildsCombinedFilterWithPaginationAndPublicationSorting() {
        Article article = article();
        ArticleFilter filter = new ArticleFilter("source-1", ArticleCategory.POLITICS, Language.SI);
        Pageable pageable = PageRequest.of(
                2,
                10,
                Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        when(mongoTemplate.count(any(Query.class), eq(Article.class))).thenReturn(21L);
        when(mongoTemplate.find(any(Query.class), eq(Article.class))).thenReturn(List.of(article));
        ArticleQueryRepositoryImpl repository = new ArticleQueryRepositoryImpl(mongoTemplate);

        Page<Article> result = repository.findAll(filter, pageable);

        ArgumentCaptor<Query> countQueryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Query> findQueryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(countQueryCaptor.capture(), eq(Article.class));
        verify(mongoTemplate).find(findQueryCaptor.capture(), eq(Article.class));

        Document criteria = findQueryCaptor.getValue().getQueryObject();
        assertThat(criteria)
                .containsEntry("sourceId", "source-1")
                .containsEntry("category", ArticleCategory.POLITICS)
                .containsEntry("originalLanguage", Language.SI);
        assertThat(countQueryCaptor.getValue().getLimit()).isZero();
        assertThat(countQueryCaptor.getValue().getSkip()).isZero();
        assertThat(findQueryCaptor.getValue().getSkip()).isEqualTo(20);
        assertThat(findQueryCaptor.getValue().getLimit()).isEqualTo(10);
        assertThat(findQueryCaptor.getValue().getSortObject())
                .containsEntry("publishedAt", -1)
                .containsEntry("id", -1);
        assertThat(result.getTotalElements()).isEqualTo(21);
        assertThat(result.getContent()).containsExactly(article);
    }

    private Article article() {
        Instant publishedAt = Instant.parse("2026-08-30T09:00:00Z");
        return new Article(
                "article-1",
                "source-1",
                "Headline",
                "https://example.com/article",
                "https://example.com/article",
                Language.SI,
                List.of(),
                publishedAt,
                publishedAt.plusSeconds(60),
                ArticleCategory.POLITICS,
                "Internal fixture content",
                publishedAt,
                publishedAt);
    }
}
