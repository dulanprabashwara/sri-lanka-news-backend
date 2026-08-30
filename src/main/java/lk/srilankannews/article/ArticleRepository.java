package lk.srilankannews.article;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ArticleRepository extends MongoRepository<Article, String>, ArticleQueryRepository {

    Optional<Article> findByCanonicalUrl(String canonicalUrl);

    boolean existsByCanonicalUrl(String canonicalUrl);
}
