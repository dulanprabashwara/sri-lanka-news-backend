package lk.srilankannews.ai;

public final class SemanticSimilarityEmbeddingInput {
    private static final String INSTRUCTION = "task: sentence similarity | query: ";

    private SemanticSimilarityEmbeddingInput() {
    }

    public static String format(String semanticContent) {
        return INSTRUCTION + semanticContent;
    }
}
