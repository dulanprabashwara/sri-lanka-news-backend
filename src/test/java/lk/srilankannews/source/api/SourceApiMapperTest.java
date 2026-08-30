package lk.srilankannews.source.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import org.junit.jupiter.api.Test;

class SourceApiMapperTest {

    private final SourceApiMapper mapper = new SourceApiMapper();

    @Test
    void mapsOnlyPublicSourceFields() {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        Source source = new Source(
                "internal-id",
                "Daily Mirror",
                "daily-mirror",
                "https://www.dailymirror.lk",
                Language.EN,
                IngestionType.RSS,
                false,
                now,
                now);

        SourceResponse response = mapper.toResponse(source);

        assertThat(response).isEqualTo(new SourceResponse(
                "Daily Mirror",
                "daily-mirror",
                "https://www.dailymirror.lk",
                Language.EN));
    }
}
