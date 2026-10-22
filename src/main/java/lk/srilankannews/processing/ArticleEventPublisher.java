/*
 * File overview: Publishes Article Event events to downstream processing.
 * Area: asynchronous article processing.
 */
package lk.srilankannews.processing;

public interface ArticleEventPublisher {
    boolean publish(ArticleDiscoveredEvent event);
}
