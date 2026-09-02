package lk.srilankannews.article.search;

import java.util.ArrayList;
import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.story.StoryEmbeddingProperties;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Repository;

@Repository
public class ArticleSemanticSearchRepository {
    static final String VECTOR_PATH = "semanticEmbedding.values";
    static final String SCORE_FIELD = "_semanticScore";
    static final int CANDIDATE_MULTIPLIER = 20;
    static final int MIN_CANDIDATES = 100;

    private final MongoOperations mongoOperations;
    private final SemanticSearchProperties searchProperties;
    private final StoryEmbeddingProperties embeddingProperties;

    public ArticleSemanticSearchRepository(MongoOperations mongoOperations,
            SemanticSearchProperties searchProperties,
            StoryEmbeddingProperties embeddingProperties) {
        this.mongoOperations = mongoOperations;
        this.searchProperties = searchProperties;
        this.embeddingProperties = embeddingProperties;
    }

    public SemanticSearchSlice search(List<Double> queryVector, ArticleSearchFilter filter,
            int page, int size) {
        List<AggregationOperation> operations = stages(queryVector, filter, page, size).stream()
                .map(stage -> (AggregationOperation) context -> stage)
                .toList();
        List<Article> matches = mongoOperations.aggregate(
                Aggregation.newAggregation(operations), Article.class, Article.class)
                .getMappedResults();
        boolean hasMore = matches.size() > size;
        return new SemanticSearchSlice(
                hasMore ? matches.subList(0, size) : matches,
                hasMore);
    }

    List<Document> stages(List<Double> queryVector, ArticleSearchFilter filter,
            int page, int size) {
        int offset = Math.multiplyExact(page, size);
        int requestedWindow = Math.min(searchProperties.maxWindow(), offset + size + 1);
        int maximumCandidates = searchProperties.maxWindow() * CANDIDATE_MULTIPLIER;
        int numCandidates = Math.min(maximumCandidates,
                Math.max(MIN_CANDIDATES, requestedWindow * CANDIDATE_MULTIPLIER));

        Document vectorSearch = new Document("index", searchProperties.vectorIndex())
                .append("path", VECTOR_PATH)
                .append("queryVector", queryVector)
                .append("numCandidates", numCandidates)
                .append("limit", requestedWindow)
                .append("filter", vectorFilter(filter));

        return List.of(
                new Document("$vectorSearch", vectorSearch),
                new Document("$set", new Document(SCORE_FIELD,
                        new Document("$meta", "vectorSearchScore"))),
                new Document("$match", new Document(SCORE_FIELD,
                        new Document("$gte", searchProperties.minScore()))),
                new Document("$sort", new Document(SCORE_FIELD, -1)
                        .append("publishedAt", -1)
                        .append("_id", -1)),
                new Document("$skip", offset),
                new Document("$limit", size + 1));
    }

    private Document vectorFilter(ArticleSearchFilter filter) {
        List<Document> conditions = new ArrayList<>();
        conditions.add(equalsFilter("processingStatus", ProcessingStatus.COMPLETED.name()));
        conditions.add(equalsFilter("semanticEmbedding.model", embeddingProperties.model()));
        conditions.add(equalsFilter(
                "semanticEmbedding.dimensions", embeddingProperties.dimensions()));
        conditions.add(equalsFilter(
                "semanticEmbedding.inputVersion", embeddingProperties.inputVersion()));
        if (filter.sourceId() != null) {
            conditions.add(equalsFilter("sourceId", filter.sourceId()));
        }
        if (filter.category() != null) {
            conditions.add(equalsFilter("category", filter.category().name()));
        }
        if (filter.language() != null) {
            conditions.add(equalsFilter("originalLanguage", filter.language().name()));
        }
        return new Document("$and", conditions);
    }

    private Document equalsFilter(String field, Object value) {
        return new Document(field, new Document("$eq", value));
    }
}
