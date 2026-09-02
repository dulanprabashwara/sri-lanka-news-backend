package lk.srilankannews.admin;

public class AdminRetryNotAllowedException extends RuntimeException {
    public AdminRetryNotAllowedException() {
        super("Only a failed Article can be retried.");
    }
}
