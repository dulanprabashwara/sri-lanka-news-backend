package lk.srilankannews.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserFollowRepository extends MongoRepository<UserFollow, String> {
    Optional<UserFollow> findByUserIdAndTargetTypeAndTargetKey(
            String userId, FollowTargetType targetType, String targetKey);
    List<UserFollow> findAllByUserIdAndTargetTypeAndTargetKeyIn(
            String userId, FollowTargetType targetType, Collection<String> targetKeys);
    long deleteByUserIdAndTargetTypeAndTargetKey(
            String userId, FollowTargetType targetType, String targetKey);
    Page<UserFollow> findByUserId(String userId, Pageable pageable);
    List<UserFollow> findAllByUserId(String userId);
    Page<UserFollow> findByUserIdAndTargetType(
            String userId, FollowTargetType targetType, Pageable pageable);

    @org.springframework.data.mongodb.repository.Query(value = "{ 'targetType': ?0, 'targetKey': ?1 }", fields = "{ 'userId': 1 }")
    List<UserFollow> findUserIdsByTargetTypeAndTargetKey(FollowTargetType targetType, String targetKey);

    @org.springframework.data.mongodb.repository.Query(value = "{ 'targetType': ?0, 'targetKey': { $in: ?1 } }", fields = "{ 'userId': 1 }")
    List<UserFollow> findUserIdsByTargetTypeAndTargetKeyIn(FollowTargetType targetType, Collection<String> targetKeys);
}
