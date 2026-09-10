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
                def = "{'lastPublishedAt': -1, 'firstPublishedAt': 1, 'category': 1}"),
        @CompoundIndex(
                name = "idx_stories_public_category_published",
                def = "{'category': 1, 'lastPublishedAt': -1, '_id': -1}"),
        @CompoundIndex(
                name = "idx_stories_public_published",
                def = "{'lastPublishedAt': -1, '_id': -1}")
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
        String matchingVersion,
        StoryRepresentativeMedia representativeMedia
) {
    public Story {
        sourceIds = sourceIds == null ? Set.of() : Set.copyOf(sourceIds);
        articleIds = articleIds == null ? Set.of() : Set.copyOf(articleIds);
    }

    public Story(
            String id, String canonicalTitle, String representativeArticleId, ArticleCategory category,
            Instant firstPublishedAt, Instant lastPublishedAt, long articleCount,
            Set<String> sourceIds, Set<String> articleIds, Instant createdAt,
            Instant updatedAt, String matchingVersion) {
        this(id, canonicalTitle, representativeArticleId, category, firstPublishedAt,
                lastPublishedAt, articleCount, sourceIds, articleIds, createdAt,
                updatedAt, matchingVersion, null);
    }

    public boolean isPubliclyVisible() {
        return articleCount >= 2 && sourceIds.size() >= 2;
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
                matchingVersion,
                article.leadMedia() == null ? null : new StoryRepresentativeMedia(
                        article.leadMedia().url(),
                        article.leadMedia().type(),
                        article.leadMedia().altText(),
                        article.leadMedia().caption(),
                        article.leadMedia().credit(),
                        article.leadMedia().width(),
                        article.leadMedia().height(),
                        article.id(),
                        article.sourceId()
                ));
    }
}
