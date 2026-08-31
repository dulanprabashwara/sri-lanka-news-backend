package lk.srilankannews.story;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface StoryRepository extends MongoRepository<Story, String> {

    Optional<Story> findByRepresentativeArticleId(String representativeArticleId);

    @Query("{'articleCount': {'$gt': 0}, 'lastPublishedAt': {'$gte': ?0},"
            + " 'firstPublishedAt': {'$lte': ?1}}")
    List<Story> findCandidates(Instant earliest, Instant latest, Pageable pageable);
}
