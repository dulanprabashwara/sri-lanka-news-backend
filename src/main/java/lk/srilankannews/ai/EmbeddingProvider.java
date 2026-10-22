/*
 * File overview: Defines the provider contract for Embedding.
 * Area: AI enrichment and embedding integrations.
 */
package lk.srilankannews.ai;

import java.util.List;

public interface EmbeddingProvider {
    List<Double> embed(String input);
}
