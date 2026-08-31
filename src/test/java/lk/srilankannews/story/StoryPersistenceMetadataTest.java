package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.api.ArticleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

class StoryPersistenceMetadataTest {

    @Test
    void storyUsesDedicatedCollectionAndCandidateIndex() {
        assertThat(Story.class.getAnnotation(Document.class).collection()).isEqualTo("stories");
        CompoundIndexes indexes = Story.class.getAnnotation(CompoundIndexes.class);
        assertThat(Arrays.stream(indexes.value())
                .map(index -> index.name()).toList())
                .contains(
                        "idx_stories_candidate_window",
                        "idx_stories_public_category_published",
                        "idx_stories_public_published");
    }

    @Test
    void articleStoryAndRepresentativeIndexesArePresent() throws Exception {
        Indexed articleIndex = Article.class.getDeclaredField("storyId")
                .getAnnotation(Indexed.class);
        Indexed representativeIndex = Story.class.getDeclaredField("representativeArticleId")
                .getAnnotation(Indexed.class);

        assertThat(articleIndex.name()).isEqualTo("idx_articles_story_id");
        assertThat(representativeIndex.unique()).isTrue();
    }

    @Test
    void internalRelationshipDoesNotLeakIntoPublicArticleDto() {
        assertThat(Arrays.stream(ArticleResponse.class.getRecordComponents())
                .map(RecordComponent::getName).toList())
                .doesNotContain("storyId", "extractedContent", "contentHash", "aiEnrichment");
    }
}
