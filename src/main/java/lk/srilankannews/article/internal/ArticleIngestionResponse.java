package lk.srilankannews.article.internal;

public record ArticleIngestionResponse(
        Status status,
        String articleId,
        String canonicalUrl
) {
    public enum Status {
        CREATED,
        DUPLICATE
    }
}
