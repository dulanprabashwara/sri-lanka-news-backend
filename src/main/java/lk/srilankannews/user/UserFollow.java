package lk.srilankannews.user;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "user_follows")
@CompoundIndexes({
        @CompoundIndex(name = "uk_user_follow_target", unique = true,
                def = "{'userId': 1, 'targetType': 1, 'targetKey': 1}"),
        @CompoundIndex(name = "idx_user_follows_created",
                def = "{'userId': 1, 'createdAt': -1, '_id': -1}")
})
public record UserFollow(
        @Id String id,
        String userId,
        FollowTargetType targetType,
        String targetKey,
        String displayLabel,
        Instant createdAt
) {
    static UserFollow create(String userId, FollowTargetType targetType, String targetKey,
            String displayLabel, Instant createdAt) {
        return new UserFollow(null, userId, targetType, targetKey, displayLabel, createdAt);
    }
}
