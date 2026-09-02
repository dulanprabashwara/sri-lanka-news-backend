package lk.srilankannews.article.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.story.StoryEmbeddingProperties;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoOperations;

class ArticleSemanticSearchRepositoryTest {
    private final SemanticSearchProperties searchProperties =
            new SemanticSearchProperties("idx_articles_semantic_vector", 0.65, 200);
    private final StoryEmbeddingProperties embeddingProperties =
            new StoryEmbeddingProperties("gemini-embedding-2", 768,
                    "story-semantic-v2", 8000, 0.82, 0.90, 50);
    private final ArticleSemanticSearchRepository repository =
            new ArticleSemanticSearchRepository(
                    org.mockito.Mockito.mock(MongoOperations.class),
                    searchProperties,
                    embeddingProperties);

    @Test
    void buildsBoundedVectorSearchAsFirstStageWithCompatibilityAndPublicFilters() {
        List<Double> vector = java.util.Collections.nCopies(768, 0.25);
        List<Document> stages = repository.stages(vector,
                new ArticleSearchFilter("source-1", ArticleCategory.SPORTS, Language.SI),
                1, 20);

        Document vectorSearch = stages.get(0).get("$vectorSearch", Document.class);
        assertThat(vectorSearch)
                .containsEntry("index", "idx_articles_semantic_vector")
                .containsEntry("path", "semanticEmbedding.values")
                .containsEntry("queryVector", vector)
                .containsEntry("numCandidates", 820)
                .containsEntry("limit", 41);
        List<Document> filters = vectorSearch.get("filter", Document.class)
                .getList("$and", Document.class);
        assertThat(filters).containsExactly(
                equality("processingStatus", "COMPLETED"),
                equality("semanticEmbedding.model", "gemini-embedding-2"),
                equality("semanticEmbedding.dimensions", 768),
                equality("semanticEmbedding.inputVersion", "story-semantic-v2"),
                equality("sourceId", "source-1"),
                equality("category", "SPORTS"),
                equality("originalLanguage", "SI"));
        assertThat(stages.get(1).get("$set", Document.class)
                .get("_semanticScore", Document.class))
                .containsEntry("$meta", "vectorSearchScore");
        assertThat(stages.get(2).get("$match", Document.class)
                .get("_semanticScore", Document.class))
                .containsEntry("$gte", 0.65);
        assertThat(stages.get(3).get("$sort", Document.class))
                .containsExactly(
                        org.assertj.core.api.Assertions.entry("_semanticScore", -1),
                        org.assertj.core.api.Assertions.entry("publishedAt", -1),
                        org.assertj.core.api.Assertions.entry("_id", -1));
        assertThat(stages.get(4)).containsEntry("$skip", 20);
        assertThat(stages.get(5)).containsEntry("$limit", 21);
    }

    @Test
    void boundsAnnCandidateCountToConfiguredMaximum() {
        Document vectorSearch = repository.stages(java.util.Collections.nCopies(768, 0.1),
                new ArticleSearchFilter(null, null, null), 0, 100)
                .get(0).get("$vectorSearch", Document.class);

        assertThat(vectorSearch.getInteger("numCandidates")).isLessThanOrEqualTo(4000);
        assertThat(vectorSearch.getInteger("numCandidates")).isGreaterThanOrEqualTo(100);
    }

    private Document equality(String field, Object value) {
        return new Document(field, new Document("$eq", value));
    }
}
