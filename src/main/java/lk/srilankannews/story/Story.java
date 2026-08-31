package lk.srilankannews.story;

import java.time.Instant;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "stories")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_stories_candidate_window",
                def = "{'lastPublishedAt': -1, 'firstPublishedAt': 1, 'category': 1}")
})
public record Story(
        @Id String id,
        String canonicalTitle,
        @Indexed(name = "uk_stories_representative_article", unique = true)
        String representativeArticleId,
        ArticleCategory category,
        Instant firstPublishedAt,
        Instant lastPublishedAt,
        long articleCount,
        Set<String> sourceIds,
        Set<String> articleIds,
        Instant createdAt,
        Instant updatedAt,
        String matchingVersion
) {
    public Story {
        sourceIds = sourceIds == null ? Set.of() : Set.copyOf(sourceIds);
        articleIds = articleIds == null ? Set.of() : Set.copyOf(articleIds);
    }

    static Story pending(Article article, Instant now, String matchingVersion) {
        return new Story(
                null,
                article.title().trim(),
                article.id(),
                article.category(),
                article.publishedAt(),
                article.publishedAt(),
                0,
                Set.of(),
                Set.of(),
                now,
                now,
                matchingVersion);
    }
}
