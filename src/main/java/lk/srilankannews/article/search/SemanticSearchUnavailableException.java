package lk.srilankannews.article.search;

public class SemanticSearchUnavailableException extends RuntimeException {
    public SemanticSearchUnavailableException(Throwable cause) {
        super("Semantic search is temporarily unavailable.", cause);
    }

    public SemanticSearchUnavailableException(String message) {
        super(message);
    }
}
