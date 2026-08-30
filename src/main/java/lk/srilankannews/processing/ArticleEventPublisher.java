package lk.srilankannews.processing;

public interface ArticleEventPublisher {
    boolean publish(ArticleDiscoveredEvent event);
}
