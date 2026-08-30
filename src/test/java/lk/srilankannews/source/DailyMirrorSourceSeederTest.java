package lk.srilankannews.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class DailyMirrorSourceSeederTest {

    @Mock
    private SourceService sourceService;

    @Test
    void createsDailyMirrorWhenMissing() {
        when(sourceService.findBySlug(DailyMirrorSourceSeeder.SOURCE_SLUG))
                .thenReturn(Optional.empty());
        DailyMirrorSourceSeeder seeder = new DailyMirrorSourceSeeder(sourceService);

        seeder.run(new DefaultApplicationArguments());

        ArgumentCaptor<CreateSourceCommand> command = ArgumentCaptor.forClass(CreateSourceCommand.class);
        verify(sourceService).create(command.capture());
        assertThat(command.getValue().name()).isEqualTo("Daily Mirror");
        assertThat(command.getValue().slug()).isEqualTo("daily-mirror");
        assertThat(command.getValue().baseUrl()).isEqualTo("https://www.dailymirror.lk");
        assertThat(command.getValue().defaultLanguage()).isEqualTo(Language.EN);
        assertThat(command.getValue().ingestionType()).isEqualTo(IngestionType.RSS);
        assertThat(command.getValue().enabled()).isTrue();
    }

    @Test
    void leavesExistingDailyMirrorSourceUnchanged() {
        when(sourceService.findBySlug(DailyMirrorSourceSeeder.SOURCE_SLUG))
                .thenReturn(Optional.of(existingSource()));
        DailyMirrorSourceSeeder seeder = new DailyMirrorSourceSeeder(sourceService);

        seeder.run(new DefaultApplicationArguments());

        verify(sourceService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    private Source existingSource() {
        Instant now = Instant.parse("2026-08-30T05:20:00Z");
        return new Source(
                "source-1",
                "Daily Mirror",
                "daily-mirror",
                "https://www.dailymirror.lk",
                Language.EN,
                IngestionType.RSS,
                true,
                now,
                now);
    }
}
