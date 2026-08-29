package lk.srilankannews.source;

public class DuplicateSourceSlugException extends RuntimeException {

    public DuplicateSourceSlugException(String slug) {
        super("A source with slug '" + slug + "' already exists.");
    }
}
