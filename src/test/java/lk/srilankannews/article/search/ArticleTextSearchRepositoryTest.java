package lk.srilankannews.article.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class ArticleTextSearchRepositoryTest {
    @Mock MongoTemplate mongoTemplate;

    @Test
    void safelyBuildsTextQueryFiltersScoreOrderingAndPagination() {
        when(mongoTemplate.count(any(Query.class), eq(Article.class))).thenReturn(42L);
        when(mongoTemplate.find(any(Query.class), eq(Article.class))).thenReturn(List.of());
        ArticleTextSearchRepository repository = new ArticleTextSearchRepository(mongoTemplate);

        repository.search("ක්‍රිකට් cricket தமிழ்",
                new ArticleSearchFilter("source-1", ArticleCategory.SPORTS, Language.SI),
                PageRequest.of(2, 10));

        ArgumentCaptor<Query> count = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Query> find = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(count.capture(), eq(Article.class));
        verify(mongoTemplate).find(find.capture(), eq(Article.class));
        Document criteria = find.getValue().getQueryObject();
        assertThat(criteria.get("$text", Document.class))
                .containsEntry("$search", "ක්‍රිකට් cricket தமிழ்");
        assertThat(criteria).containsEntry("sourceId", "source-1")
                .containsEntry("category", ArticleCategory.SPORTS)
                .containsEntry("originalLanguage", Language.SI);
        assertThat(find.getValue().getFieldsObject().get(
                ArticleTextSearchRepository.SCORE_FIELD, Document.class))
                .containsEntry("$meta", "textScore");
        assertThat(find.getValue().getSortObject().get(
                ArticleTextSearchRepository.SCORE_FIELD, Document.class))
                .containsEntry("$meta", "textScore");
        assertThat(find.getValue().getSortObject()).containsEntry("publishedAt", -1)
                .containsEntry("id", -1);
        assertThat(find.getValue().getSkip()).isEqualTo(20);
        assertThat(find.getValue().getLimit()).isEqualTo(10);
        assertThat(count.getValue().getFieldsObject()).doesNotContainKey(
                ArticleTextSearchRepository.SCORE_FIELD);
    }
}
