package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CosineSimilarityTest {
    @Test
    void returnsOneForIdenticalDirection() {
        assertThat(CosineSimilarity.calculate(
                List.of(1.0, 2.0, 3.0), List.of(2.0, 4.0, 6.0)))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.000001));
    }

    @Test
    void returnsZeroForOrthogonalVectors() {
        assertThat(CosineSimilarity.calculate(
                List.of(1.0, 0.0), List.of(0.0, 1.0))).isZero();
    }
}
