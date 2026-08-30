package lk.srilankannews.processing;

public interface ArticleEventStream extends ArticleEventPublisher {
    boolean publishDeadLetter(ArticleDiscoveredEvent event, String reason);

    void acknowledge(String recordId);
}
