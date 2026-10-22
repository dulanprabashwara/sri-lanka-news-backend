/*
 * File overview: Supports Semantic Similarity Embedding Input behavior within the backend.
 * Area: AI enrichment and embedding integrations.
 */
package lk.srilankannews.ai;

public final class SemanticSimilarityEmbeddingInput {
    private static final String INSTRUCTION = "task: sentence similarity | query: ";

    private SemanticSimilarityEmbeddingInput() {
    }

    public static String format(String semanticContent) {
        return INSTRUCTION + semanticContent;
    }
}
