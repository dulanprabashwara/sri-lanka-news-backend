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
import lk.srilankannews.ingestion.settings.IngestionSourceSettingsService;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class PublisherSourceSeedersTest {

    @Mock
    private SourceService sourceService;
    @Mock
    private IngestionSourceSettingsService ingestionSettingsService;

    @Test
    void createsLakbimaAndConfiguresPublisherTransition() {
        when(sourceService.findBySlug(LakbimaNewsSourceSeeder.SOURCE_SLUG))
                .thenReturn(Optional.empty());

        new LakbimaNewsSourceSeeder(sourceService, ingestionSettingsService)
                .run(new DefaultApplicationArguments());

        CreateSourceCommand command = capturedCommand();
        assertThat(command.name()).isEqualTo("Lakbima News");
        assertThat(command.slug()).isEqualTo("lakbima-news");
        assertThat(command.baseUrl()).isEqualTo("https://lakbima.news");
        assertThat(command.defaultLanguage()).isEqualTo(Language.SI);
        assertThat(command.ingestionType()).isEqualTo(IngestionType.RSS);
        assertThat(command.enabled()).isTrue();
        verify(ingestionSettingsService).ensureDefaults("lakbima-news", true, 15, 120);
        verify(ingestionSettingsService).disableIfPresent("hiru-news-sinhala");
    }

    @Test
    void existingLakbimaIsNotDuplicatedAndTransitionStillRuns() {
        when(sourceService.findBySlug(LakbimaNewsSourceSeeder.SOURCE_SLUG))
                .thenReturn(Optional.of(existingSource(
                        "Lakbima News", "lakbima-news", Language.SI, IngestionType.RSS)));

        new LakbimaNewsSourceSeeder(sourceService, ingestionSettingsService)
                .run(new DefaultApplicationArguments());

        verify(sourceService, never()).create(org.mockito.ArgumentMatchers.any());
        verify(ingestionSettingsService).ensureDefaults("lakbima-news", true, 15, 120);
        verify(ingestionSettingsService).disableIfPresent("hiru-news-sinhala");
    }

    @Test
    void createsNewsFirstWhenMissing() {
        when(sourceService.findBySlug(NewsFirstSourceSeeder.SOURCE_SLUG))
                .thenReturn(Optional.empty());

        new NewsFirstSourceSeeder(sourceService).run(new DefaultApplicationArguments());

        CreateSourceCommand command = capturedCommand();
        assertThat(command.name()).isEqualTo("NewsFirst");
        assertThat(command.slug()).isEqualTo("newsfirst");
        assertThat(command.baseUrl()).isEqualTo("https://www.newsfirst.lk");
        assertThat(command.defaultLanguage()).isEqualTo(Language.EN);
        assertThat(command.ingestionType()).isEqualTo(IngestionType.HTML);
        assertThat(command.enabled()).isTrue();
    }

    @Test
    void createsNewswireWhenMissing() {
        when(sourceService.findBySlug(NewswireSourceSeeder.SOURCE_SLUG))
                .thenReturn(Optional.empty());

        new NewswireSourceSeeder(sourceService)
                .run(new DefaultApplicationArguments());

        CreateSourceCommand command = capturedCommand();
        assertThat(command.name()).isEqualTo("Newswire");
        assertThat(command.slug()).isEqualTo("newswire");
        assertThat(command.baseUrl()).isEqualTo("https://www.newswire.lk");
        assertThat(command.defaultLanguage()).isEqualTo(Language.EN);
        assertThat(command.ingestionType()).isEqualTo(IngestionType.RSS);
        assertThat(command.enabled()).isTrue();
    }

    @Test
    void leavesExistingNewswireUnchanged() {
        when(sourceService.findBySlug(NewswireSourceSeeder.SOURCE_SLUG))
                .thenReturn(Optional.of(existingSource(
                        "Newswire",
                        "newswire",
                        Language.EN,
                        IngestionType.RSS)));

        new NewswireSourceSeeder(sourceService)
                .run(new DefaultApplicationArguments());

        verify(sourceService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    private CreateSourceCommand capturedCommand() {
        ArgumentCaptor<CreateSourceCommand> command =
                ArgumentCaptor.forClass(CreateSourceCommand.class);
        verify(sourceService).create(command.capture());
        return command.getValue();
    }

    private Source existingSource(
            String name, String slug, Language language, IngestionType ingestionType) {
        Instant now = Instant.parse("2026-08-30T05:20:00Z");
        return new Source(
                "source-1",
                name,
                slug,
                "https://example.com",
                language,
                ingestionType,
                true,
                now,
                now);
    }
}
