package lk.srilankannews.article;

public class DuplicateArticleContentException extends RuntimeException {

    public DuplicateArticleContentException(String contentHash) {
        super("An article with content hash '%s' already exists.".formatted(contentHash));
    }
}
