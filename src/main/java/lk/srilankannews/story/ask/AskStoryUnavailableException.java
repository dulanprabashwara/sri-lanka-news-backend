package lk.srilankannews.story.ask;

public class AskStoryUnavailableException extends RuntimeException {
    public AskStoryUnavailableException(String message) {
        super(message);
    }

    public AskStoryUnavailableException(Throwable cause) {
        super("Ask This Story is temporarily unavailable.", cause);
    }
}
