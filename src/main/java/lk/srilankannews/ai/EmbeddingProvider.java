package lk.srilankannews.ai;

import java.util.List;

public interface EmbeddingProvider {
    List<Double> embed(String input);
}
