package lk.srilankannews.admin.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lk.srilankannews.ingestion.run.IngestionRun;
import lk.srilankannews.ingestion.run.IngestionRunStatus;
import lk.srilankannews.ingestion.run.IngestionTriggerType;
import lk.srilankannews.ingestion.settings.IngestionSourceSettings;
import org.junit.jupiter.api.Test;

class IngestionHealthCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final IngestionSourceSettings ENABLED_SETTINGS = new IngestionSourceSettings(
            "id", "src1", "daily-mirror", true, 10, 120, NOW, NOW, "SYSTEM"
    );
    private static final IngestionSourceSettings DISABLED_SETTINGS = new IngestionSourceSettings(
            "id", "src1", "daily-mirror", false, 10, 120, NOW, NOW, "SYSTEM"
    );

    private IngestionRun run(IngestionRunStatus status, Instant startedAt) {
        return new IngestionRun(
                "run-1", "src1", "daily-mirror", IngestionTriggerType.SCHEDULED, status,
                startedAt, startedAt, startedAt.plus(1, ChronoUnit.MINUTES),
                "worker-1", NOW.plus(5, ChronoUnit.MINUTES),
                10, 10, 9, status == IngestionRunStatus.FAILED ? 1 : 0,
                null, null, startedAt, startedAt, null
        );
    }

    @Test
    void pausedSourceReturnsPaused() {
        var stats = IngestionHealthCalculator.calculate(DISABLED_SETTINGS, List.of(
                run(IngestionRunStatus.COMPLETED, NOW.minus(5, ChronoUnit.MINUTES))
        ), NOW);
        assertThat(stats.health()).isEqualTo(IngestionHealthCalculator.PAUSED);
    }

    @Test
    void noRunsReturnsNeverRun() {
        var stats = IngestionHealthCalculator.calculate(ENABLED_SETTINGS, List.of(), NOW);
        assertThat(stats.health()).isEqualTo(IngestionHealthCalculator.NEVER_RUN);
    }

    @Test
    void threeConsecutiveFailuresReturnsFailing() {
        var stats = IngestionHealthCalculator.calculate(ENABLED_SETTINGS, List.of(
                run(IngestionRunStatus.FAILED, NOW.minus(1, ChronoUnit.MINUTES)),
                run(IngestionRunStatus.FAILED, NOW.minus(11, ChronoUnit.MINUTES)),
                run(IngestionRunStatus.FAILED, NOW.minus(21, ChronoUnit.MINUTES))
        ), NOW);
        assertThat(stats.health()).isEqualTo(IngestionHealthCalculator.FAILING);
        assertThat(stats.consecutiveFailures()).isEqualTo(3);
    }

    @Test
    void noRecentSuccessReturnsStale() {
        var stats = IngestionHealthCalculator.calculate(ENABLED_SETTINGS, List.of(
                run(IngestionRunStatus.COMPLETED, NOW.minus(60, ChronoUnit.MINUTES))
        ), NOW);
        assertThat(stats.health()).isEqualTo(IngestionHealthCalculator.STALE);
    }

    @Test
    void singleFailureReturnsWarning() {
        var stats = IngestionHealthCalculator.calculate(ENABLED_SETTINGS, List.of(
                run(IngestionRunStatus.FAILED, NOW.minus(5, ChronoUnit.MINUTES)),
                run(IngestionRunStatus.COMPLETED, NOW.minus(15, ChronoUnit.MINUTES))
        ), NOW);
        assertThat(stats.health()).isEqualTo(IngestionHealthCalculator.WARNING);
    }

    @Test
    void interruptedDoesNotCountAsFailure() {
        var stats = IngestionHealthCalculator.calculate(ENABLED_SETTINGS, List.of(
                run(IngestionRunStatus.INTERRUPTED, NOW.minus(5, ChronoUnit.MINUTES)),
                run(IngestionRunStatus.COMPLETED, NOW.minus(15, ChronoUnit.MINUTES))
        ), NOW);
        // INTERRUPTED produces WARNING but does not increment consecutive failures
        assertThat(stats.health()).isEqualTo(IngestionHealthCalculator.WARNING);
        assertThat(stats.consecutiveFailures()).isEqualTo(0);
    }

    @Test
    void recentCompletedReturnsHealthy() {
        var stats = IngestionHealthCalculator.calculate(ENABLED_SETTINGS, List.of(
                run(IngestionRunStatus.COMPLETED, NOW.minus(5, ChronoUnit.MINUTES))
        ), NOW);
        assertThat(stats.health()).isEqualTo(IngestionHealthCalculator.HEALTHY);
    }

    @Test
    void precedence_pausedTakesPrecedenceOverEverything() {
        // Even with 3 failures, disabled = PAUSED
        var stats = IngestionHealthCalculator.calculate(DISABLED_SETTINGS, List.of(
                run(IngestionRunStatus.FAILED, NOW.minus(1, ChronoUnit.MINUTES)),
                run(IngestionRunStatus.FAILED, NOW.minus(11, ChronoUnit.MINUTES)),
                run(IngestionRunStatus.FAILED, NOW.minus(21, ChronoUnit.MINUTES))
        ), NOW);
        assertThat(stats.health()).isEqualTo(IngestionHealthCalculator.PAUSED);
    }
}
