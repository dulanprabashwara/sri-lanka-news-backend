package lk.srilankannews.article;

public class UnknownArticleSourceException extends RuntimeException {

    public UnknownArticleSourceException(String sourceId) {
        super("Source '" + sourceId + "' does not exist.");
    }
}
