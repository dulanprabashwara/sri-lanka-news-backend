package lk.srilankannews.article;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ArticleSemanticEmbedding(
        List<Double> values,
        String model,
        int dimensions,
        String inputVersion,
        String inputHash,
        Instant embeddedAt) {

    public ArticleSemanticEmbedding {
        values = List.copyOf(values);
    }

    public boolean matches(
            String expectedModel,
            int expectedDimensions,
            String expectedInputVersion,
            String expectedInputHash) {
        return Objects.equals(model, expectedModel)
                && dimensions == expectedDimensions
                && Objects.equals(inputVersion, expectedInputVersion)
                && Objects.equals(inputHash, expectedInputHash);
    }
}
