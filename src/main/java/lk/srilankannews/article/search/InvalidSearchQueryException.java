package lk.srilankannews.article.search;

public class InvalidSearchQueryException extends RuntimeException {
    public InvalidSearchQueryException(String message) {
        super(message);
    }
}
