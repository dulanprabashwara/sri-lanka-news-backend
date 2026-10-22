/*
 * File overview: Defines or implements the event stream used for Article Event.
 * Area: asynchronous article processing.
 */
package lk.srilankannews.processing;

public interface ArticleEventStream extends ArticleEventPublisher {
    boolean publishDeadLetter(ArticleDiscoveredEvent event, String reason);

    void acknowledge(String recordId);
}
