package lk.srilankannews.user;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "user_bookmarks")
@CompoundIndexes({
        @CompoundIndex(name = "uk_user_bookmark_target", unique = true,
                def = "{'userId': 1, 'targetType': 1, 'targetId': 1}"),
        @CompoundIndex(name = "idx_user_bookmarks_created",
                def = "{'userId': 1, 'createdAt': -1, '_id': -1}")
})
public record UserBookmark(
        @Id String id,
        String userId,
        BookmarkTargetType targetType,
        String targetId,
        Instant createdAt
) {
    public static UserBookmark create(
            String userId, BookmarkTargetType targetType, String targetId, Instant createdAt) {
        return new UserBookmark(null, userId, targetType, targetId, createdAt);
    }
}
