package lk.srilankannews.source;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SourceRepository extends MongoRepository<Source, String> {

    Optional<Source> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
