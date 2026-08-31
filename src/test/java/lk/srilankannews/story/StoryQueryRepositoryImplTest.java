package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class StoryQueryRepositoryImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void buildsPublicStoryFiltersAndDeterministicPagination() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-31T23:59:59Z");
        StoryFilter filter = new StoryFilter(ArticleCategory.LOCAL, from, to);
        var pageable = PageRequest.of(
                1, 20, Sort.by(Sort.Direction.DESC, "lastPublishedAt")
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        when(mongoTemplate.count(any(Query.class), eq(Story.class))).thenReturn(21L);
        when(mongoTemplate.find(any(Query.class), eq(Story.class))).thenReturn(List.of());
        StoryQueryRepositoryImpl repository = new StoryQueryRepositoryImpl(mongoTemplate);

        var result = repository.findAll(filter, pageable);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Story.class));
        Document query = queryCaptor.getValue().getQueryObject();
        assertThat((Document) query.get("articleCount")).containsEntry("$gt", 0);
        assertThat(query).containsEntry("category", ArticleCategory.LOCAL);
        assertThat((Document) query.get("lastPublishedAt"))
                .containsEntry("$gte", from)
                .containsEntry("$lte", to);
        assertThat(queryCaptor.getValue().getSkip()).isEqualTo(20);
        assertThat(queryCaptor.getValue().getLimit()).isEqualTo(20);
        assertThat(queryCaptor.getValue().getSortObject())
                .containsEntry("lastPublishedAt", -1)
                .containsEntry("id", -1);
        assertThat(result.getTotalElements()).isEqualTo(21);
    }
}
