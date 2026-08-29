package lk.srilankannews.article;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.common.domain.Language;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "articles")
public record Article(
        @Id String id,
        String sourceId,
        String title,
        String originalUrl,
        @Indexed(name = "uk_articles_canonical_url", unique = true) String canonicalUrl,
        Language originalLanguage,
        List<String> authors,
        Instant publishedAt,
        Instant discoveredAt,
        ArticleCategory category,
        Instant createdAt,
        Instant updatedAt
) {
    public Article {
        authors = authors == null ? List.of() : List.copyOf(authors);
    }

    static Article create(CreateArticleCommand command, Instant now) {
        return new Article(
                null,
                command.sourceId(),
                command.title(),
                command.originalUrl(),
                command.canonicalUrl(),
                command.originalLanguage(),
                command.authors(),
                command.publishedAt(),
                command.discoveredAt(),
                command.category(),
                now,
                now);
    }
}
