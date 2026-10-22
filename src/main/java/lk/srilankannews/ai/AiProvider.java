/*
 * File overview: Defines the provider contract for AI.
 * Area: AI enrichment and embedding integrations.
 */
package lk.srilankannews.ai;

public interface AiProvider {
    AiResult enrich(AiInput input);
}
