package lk.srilankannews.article;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lk.srilankannews.common.domain.Language;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "articles")
@CompoundIndexes({
        @CompoundIndex(name = "idx_articles_published_id", def = "{'publishedAt': -1, '_id': -1}"),
        @CompoundIndex(name = "idx_articles_source_published", def = "{'sourceId': 1, 'publishedAt': -1}"),
        @CompoundIndex(name = "idx_articles_category_published", def = "{'category': 1, 'publishedAt': -1}"),
        @CompoundIndex(name = "idx_articles_language_published", def = "{'originalLanguage': 1, 'publishedAt': -1}")
})
public record Article(
        @Id String id,
        String sourceId,
        String title,
        String originalUrl,
        @Indexed(name = "uk_articles_canonical_url", unique = true) String canonicalUrl,
        Language originalLanguage,
        List<String> authors,
        @Indexed(name = "idx_articles_published_at", direction = IndexDirection.DESCENDING)
        Instant publishedAt,
        Instant discoveredAt,
        ArticleCategory category,
        String extractedContent,
        @Indexed(name = "uk_articles_content_hash", unique = true, sparse = true) String contentHash,
        ArticleAiEnrichment aiEnrichment,
        ArticleSemanticEmbedding semanticEmbedding,
        Map<Language, ArticleTranslation> translations,
        ArticleLeadMedia leadMedia,
        ProcessingStatus processingStatus,
        @Indexed(name = "idx_articles_story_id") String storyId,
        Instant createdAt,
        Instant updatedAt
) {
    public Article {
        authors = authors == null ? List.of() : List.copyOf(authors);
        translations = translations == null ? Map.of() : Map.copyOf(translations);
    }

    public Article(
            String id, String sourceId, String title, String originalUrl, String canonicalUrl,
            Language originalLanguage, List<String> authors, Instant publishedAt,
            Instant discoveredAt, ArticleCategory category, String extractedContent,
            String contentHash, ArticleAiEnrichment aiEnrichment,
            ArticleSemanticEmbedding semanticEmbedding, Map<Language, ArticleTranslation> translations,
            ProcessingStatus processingStatus, String storyId, Instant createdAt, Instant updatedAt) {
        this(id, sourceId, title, originalUrl, canonicalUrl, originalLanguage, authors, publishedAt, discoveredAt, category, extractedContent, contentHash, aiEnrichment, semanticEmbedding, translations, null, processingStatus, storyId, createdAt, updatedAt);
    }

    public Article(
            String id, String sourceId, String title, String originalUrl, String canonicalUrl,
            Language originalLanguage, List<String> authors, Instant publishedAt,
            Instant discoveredAt, ArticleCategory category, String extractedContent,
            String contentHash, ProcessingStatus processingStatus,
            Instant createdAt, Instant updatedAt) {
        this(id, sourceId, title, originalUrl, canonicalUrl, originalLanguage, authors,
                publishedAt, discoveredAt, category, extractedContent, contentHash, null, null,
                Map.of(), null, processingStatus, null, createdAt, updatedAt);
    }

    public Article(
            String id, String sourceId, String title, String originalUrl, String canonicalUrl,
            Language originalLanguage, List<String> authors, Instant publishedAt,
            Instant discoveredAt, ArticleCategory category, String extractedContent,
            String contentHash, ArticleAiEnrichment aiEnrichment,
            ArticleSemanticEmbedding semanticEmbedding, ProcessingStatus processingStatus,
            String storyId, Instant createdAt, Instant updatedAt) {
        this(id, sourceId, title, originalUrl, canonicalUrl, originalLanguage, authors,
                publishedAt, discoveredAt, category, extractedContent, contentHash, aiEnrichment,
                semanticEmbedding, Map.of(), null, processingStatus, storyId, createdAt, updatedAt);
    }

    public Article(
            String id, String sourceId, String title, String originalUrl, String canonicalUrl,
            Language originalLanguage, List<String> authors, Instant publishedAt,
            Instant discoveredAt, ArticleCategory category, String extractedContent,
            String contentHash, ArticleAiEnrichment aiEnrichment, ProcessingStatus processingStatus,
            Instant createdAt, Instant updatedAt) {
        this(id, sourceId, title, originalUrl, canonicalUrl, originalLanguage, authors,
                publishedAt, discoveredAt, category, extractedContent, contentHash, aiEnrichment,
                null, Map.of(), null, processingStatus, null, createdAt, updatedAt);
    }

    public Article(
            String id, String sourceId, String title, String originalUrl, String canonicalUrl,
            Language originalLanguage, List<String> authors, Instant publishedAt,
            Instant discoveredAt, ArticleCategory category, String extractedContent,
            String contentHash, ArticleAiEnrichment aiEnrichment,
            ProcessingStatus processingStatus, String storyId,
            Instant createdAt, Instant updatedAt) {
        this(id, sourceId, title, originalUrl, canonicalUrl, originalLanguage, authors,
                publishedAt, discoveredAt, category, extractedContent, contentHash, aiEnrichment,
                null, Map.of(), null, processingStatus, storyId, createdAt, updatedAt);
    }

    public Article(
            String id, String sourceId, String title, String originalUrl, String canonicalUrl,
            Language originalLanguage, List<String> authors, Instant publishedAt,
            Instant discoveredAt, ArticleCategory category, String extractedContent,
            Instant createdAt, Instant updatedAt) {
        this(id, sourceId, title, originalUrl, canonicalUrl, originalLanguage, authors,
                publishedAt, discoveredAt, category, extractedContent, null, null, null, Map.of(),
                null, ProcessingStatus.PENDING, null, createdAt, updatedAt);
    }

    static Article create(CreateArticleCommand command, String contentHash, Instant now) {
        return new Article(
                null, command.sourceId(), command.title(), command.originalUrl(),
                command.canonicalUrl(), command.originalLanguage(), command.authors(),
                command.publishedAt(), command.discoveredAt(), command.category(),
                command.extractedContent(), contentHash, null, null, Map.of(), null, ProcessingStatus.PENDING,
                null, now, now);
    }

    Article withProcessingStatus(ProcessingStatus status, Instant now) {
        return new Article(id, sourceId, title, originalUrl, canonicalUrl, originalLanguage,
                authors, publishedAt, discoveredAt, category, extractedContent, contentHash,
                aiEnrichment, semanticEmbedding, translations, leadMedia, status, storyId, createdAt, now);
    }

    Article withAiEnrichment(
            ArticleAiEnrichment enrichment, ArticleCategory enrichedCategory, Instant now) {
        return new Article(id, sourceId, title, originalUrl, canonicalUrl, originalLanguage,
                authors, publishedAt, discoveredAt, enrichedCategory, extractedContent, contentHash,
                enrichment, null, translations, leadMedia, ProcessingStatus.COMPLETED, storyId, createdAt, now);
    }

    Article withSemanticEmbedding(ArticleSemanticEmbedding embedding, Instant now) {
        return new Article(id, sourceId, title, originalUrl, canonicalUrl, originalLanguage,
                authors, publishedAt, discoveredAt, category, extractedContent, contentHash,
                aiEnrichment, embedding, translations, leadMedia, processingStatus, storyId, createdAt, now);
    }

    Article withTranslations(Map<Language, ArticleTranslation> updatedTranslations, Instant now) {
        return new Article(id, sourceId, title, originalUrl, canonicalUrl, originalLanguage,
                authors, publishedAt, discoveredAt, category, extractedContent, contentHash,
                aiEnrichment, semanticEmbedding, updatedTranslations, leadMedia, processingStatus, storyId,
                createdAt, now);
    }
}
