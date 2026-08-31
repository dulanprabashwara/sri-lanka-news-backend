package lk.srilankannews.article;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ArticleRepository extends MongoRepository<Article, String>, ArticleQueryRepository {

    Optional<Article> findByCanonicalUrl(String canonicalUrl);

    Optional<Article> findByContentHash(String contentHash);

    boolean existsByCanonicalUrl(String canonicalUrl);

    boolean existsByContentHash(String contentHash);

    @Query("{'$or': [{'processingStatus': null}, {'processingStatus': 'PENDING'},"
            + " {'processingStatus': 'PROCESSING'}, {'processingStatus': 'RETRYING'},"
            + " {'processingStatus': 'COMPLETED', 'aiEnrichment': null}]}")
    List<Article> findAwaitingProcessing();

    @Query("{'aiEnrichment': {'$ne': null}, '$or': ["
            + "{'semanticEmbedding': null}, {'semanticEmbedding': {'$exists': false}},"
            + "{'semanticEmbedding.model': {'$ne': ?0}},"
            + "{'semanticEmbedding.dimensions': {'$ne': ?1}},"
            + "{'semanticEmbedding.inputVersion': {'$ne': ?2}}]}")
    List<Article> findEmbeddingBackfillCandidates(
            String model, int dimensions, String inputVersion, Pageable pageable);

    @Query("{'aiEnrichment': {'$ne': null}, '$or': ["
            + "{'storyId': null}, {'storyId': {'$exists': false}}]}")
    List<Article> findEnrichedWithoutStory(Pageable pageable);
}
