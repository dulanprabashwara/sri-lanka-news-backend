package lk.srilankannews.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T10:15:30Z");

    @Mock
    private SourceRepository sourceRepository;

    private SourceService sourceService;

    @BeforeEach
    void setUp() {
        sourceService = new SourceService(sourceRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsSourceWithUtcAuditTimestamps() {
        CreateSourceCommand command = validCommand();
        when(sourceRepository.save(any(Source.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Source created = sourceService.create(command);

        assertThat(created.id()).isNull();
        assertThat(created.name()).isEqualTo(command.name());
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.updatedAt()).isEqualTo(NOW);
        verify(sourceRepository).existsBySlug(command.slug());
        verify(sourceRepository).save(created);
    }

    @Test
    void rejectsKnownDuplicateSlugBeforeSaving() {
        CreateSourceCommand command = validCommand();
        when(sourceRepository.existsBySlug(command.slug())).thenReturn(true);

        assertThatThrownBy(() -> sourceService.create(command))
                .isInstanceOf(DuplicateSourceSlugException.class);

        verify(sourceRepository, never()).save(any());
    }

    @Test
    void translatesDatabaseDuplicateFromConcurrentInsert() {
        CreateSourceCommand command = validCommand();
        when(sourceRepository.save(any(Source.class)))
                .thenThrow(new DuplicateKeyException("uk_sources_slug"));

        assertThatThrownBy(() -> sourceService.create(command))
                .isInstanceOf(DuplicateSourceSlugException.class);
    }

    @Test
    void disablesExistingSourceAndUpdatesTimestamp() {
        Instant createdAt = Instant.parse("2026-08-29T10:15:30Z");
        Source existing = new Source(
                "source-1",
                "Newswire",
                "newswire",
                "https://www.newswire.lk",
                Language.EN,
                IngestionType.RSS,
                true,
                createdAt,
                createdAt);
        when(sourceRepository.findBySlug(existing.slug()))
                .thenReturn(Optional.of(existing));
        when(sourceRepository.save(any(Source.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Source disabled = sourceService
                .setEnabledBySlug(existing.slug(), false)
                .orElseThrow();

        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.createdAt()).isEqualTo(createdAt);
        assertThat(disabled.updatedAt()).isEqualTo(NOW);
        verify(sourceRepository).save(disabled);
    }

    private CreateSourceCommand validCommand() {
        return new CreateSourceCommand(
                "Daily Mirror",
                "daily-mirror",
                "https://www.dailymirror.lk",
                Language.EN,
                IngestionType.RSS,
                true,
                    new lk.srilankannews.source.SourceImagePolicy(false, java.util.Set.of()));
    }
}
