package lk.srilankannews.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserBookmarkRepository extends MongoRepository<UserBookmark, String> {
    Optional<UserBookmark> findByUserIdAndTargetTypeAndTargetId(
            String userId, BookmarkTargetType targetType, String targetId);
    boolean existsByUserIdAndTargetTypeAndTargetId(
            String userId, BookmarkTargetType targetType, String targetId);
    long deleteByUserIdAndTargetTypeAndTargetId(
            String userId, BookmarkTargetType targetType, String targetId);
    Page<UserBookmark> findByUserId(String userId, Pageable pageable);
    Page<UserBookmark> findByUserIdAndTargetType(
            String userId, BookmarkTargetType targetType, Pageable pageable);
}
