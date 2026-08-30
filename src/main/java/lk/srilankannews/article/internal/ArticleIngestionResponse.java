package lk.srilankannews.article.internal;

public record ArticleIngestionResponse(
        Status status,
        String articleId,
        String canonicalUrl,
        DuplicateReason duplicateReason
) {
    public enum Status {
        CREATED,
        DUPLICATE
    }

    public enum DuplicateReason {
        URL_DUPLICATE,
        CONTENT_DUPLICATE
    }
}
