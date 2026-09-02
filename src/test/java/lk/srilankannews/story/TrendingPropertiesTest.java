package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class TrendingPropertiesTest {

    @Test
    void validatesSafeConfigurationRanges() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(new TrendingProperties(72, 12, 3, 5, 500)))
                    .isEmpty();
            assertThat(validator.validate(new TrendingProperties(5, 0, 0, 0, 49)))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder(
                            "windowHours", "recencyHalfLifeHours", "sourceNormalization",
                            "reportNormalization", "maxCandidates");
            assertThat(validator.validate(new TrendingProperties(337, 12, 3, 5, 5001)))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("windowHours", "maxCandidates");
        }
    }
}
