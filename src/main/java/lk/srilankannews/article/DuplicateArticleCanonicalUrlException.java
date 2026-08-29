package lk.srilankannews.article;

public class DuplicateArticleCanonicalUrlException extends RuntimeException {

    public DuplicateArticleCanonicalUrlException(String canonicalUrl) {
        super("An article with canonical URL '" + canonicalUrl + "' already exists.");
    }
}
