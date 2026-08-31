package lk.srilankannews.article;

import java.util.List;
import java.util.Optional;
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
}
