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
class PublisherSourceSeedersTest {

    @Mock
    private SourceService sourceService;

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
    void createsAdaDeranaSinhalaWhenMissing() {
        when(sourceService.findBySlug(AdaDeranaSinhalaSourceSeeder.SOURCE_SLUG))
                .thenReturn(Optional.empty());

        new AdaDeranaSinhalaSourceSeeder(sourceService)
                .run(new DefaultApplicationArguments());

        CreateSourceCommand command = capturedCommand();
        assertThat(command.name()).isEqualTo("Ada Derana Sinhala");
        assertThat(command.slug()).isEqualTo("ada-derana-sinhala");
        assertThat(command.baseUrl()).isEqualTo("https://sinhala.adaderana.lk");
        assertThat(command.defaultLanguage()).isEqualTo(Language.SI);
        assertThat(command.ingestionType()).isEqualTo(IngestionType.RSS);
        assertThat(command.enabled()).isFalse();
    }

    @Test
    void createsHiruNewsSinhalaWhenMissing() {
        when(sourceService.findBySlug(HiruNewsSinhalaSourceSeeder.SOURCE_SLUG))
                .thenReturn(Optional.empty());

        new HiruNewsSinhalaSourceSeeder(sourceService)
                .run(new DefaultApplicationArguments());

        CreateSourceCommand command = capturedCommand();
        assertThat(command.name()).isEqualTo("Hiru News");
        assertThat(command.slug()).isEqualTo("hiru-news-sinhala");
        assertThat(command.baseUrl()).isEqualTo("https://www.hirunews.lk");
        assertThat(command.defaultLanguage()).isEqualTo(Language.SI);
        assertThat(command.ingestionType()).isEqualTo(IngestionType.HTML);
        assertThat(command.enabled()).isTrue();
    }

    @Test
    void leavesExistingNewsFirstUnchanged() {
        when(sourceService.findBySlug(NewsFirstSourceSeeder.SOURCE_SLUG))
                .thenReturn(Optional.of(existingSource(
                        "NewsFirst", "newsfirst", Language.EN, IngestionType.HTML)));

        new NewsFirstSourceSeeder(sourceService).run(new DefaultApplicationArguments());

        verify(sourceService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void leavesExistingHiruNewsSinhalaUnchanged() {
        when(sourceService.findBySlug(HiruNewsSinhalaSourceSeeder.SOURCE_SLUG))
                .thenReturn(Optional.of(existingSource(
                        "Hiru News",
                        "hiru-news-sinhala",
                        Language.SI,
                        IngestionType.HTML)));

        new HiruNewsSinhalaSourceSeeder(sourceService)
                .run(new DefaultApplicationArguments());

        verify(sourceService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void leavesExistingAdaDeranaSinhalaUnchanged() {
        when(sourceService.findBySlug(AdaDeranaSinhalaSourceSeeder.SOURCE_SLUG))
                .thenReturn(Optional.of(existingSource(
                        "Ada Derana Sinhala",
                        "ada-derana-sinhala",
                        Language.SI,
                        IngestionType.RSS)));

        new AdaDeranaSinhalaSourceSeeder(sourceService)
                .run(new DefaultApplicationArguments());

        verify(sourceService, never()).create(org.mockito.ArgumentMatchers.any());
        verify(sourceService).setEnabledBySlug("ada-derana-sinhala", false);
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
