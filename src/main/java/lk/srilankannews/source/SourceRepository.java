package lk.srilankannews.source;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SourceRepository extends MongoRepository<Source, String> {

    Optional<Source> findBySlug(String slug);

    List<Source> findAllByOrderByNameAsc();

    List<Source> findAllBySlugIn(Collection<String> slugs);

    boolean existsBySlug(String slug);
}
