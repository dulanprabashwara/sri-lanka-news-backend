package lk.srilankannews.admin.ingestion;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lk.srilankannews.ingestion.run.IngestionRun;
import lk.srilankannews.ingestion.run.IngestionRunStatus;
import lk.srilankannews.ingestion.settings.IngestionSourceSettings;

public class IngestionHealthCalculator {

    public static final String HEALTHY = "HEALTHY";
    public static final String WARNING = "WARNING";
    public static final String STALE = "STALE";
    public static final String FAILING = "FAILING";
    public static final String NEVER_RUN = "NEVER_RUN";
    public static final String PAUSED = "PAUSED";

    public static HealthStats calculate(IngestionSourceSettings settings, List<IngestionRun> recentRuns, Instant now) {
        if (!settings.enabled()) {
            return buildStats(PAUSED, recentRuns);
        }

        if (recentRuns == null || recentRuns.isEmpty()) {
            return buildStats(NEVER_RUN, recentRuns);
        }

        int consecutiveFailures = countConsecutiveTerminalFailures(recentRuns);
        IngestionRun lastRun = recentRuns.get(0);
        
        Instant lastSuccessAt = findLastSuccessAt(recentRuns);

        // Precedence: FAILING > STALE > WARNING > HEALTHY
        
        if (consecutiveFailures >= 3) {
            return buildStats(FAILING, recentRuns);
        }

        if (lastSuccessAt != null) {
            long minutesSinceSuccess = ChronoUnit.MINUTES.between(lastSuccessAt, now);
            if (minutesSinceSuccess > (settings.intervalMinutes() * 3L)) {
                return buildStats(STALE, recentRuns);
            }
        } else {
            // No success found in recent runs. See if it's been a long time since the first attempt.
            long minutesSinceAttempt = ChronoUnit.MINUTES.between(lastRun.startedAt(), now);
            if (minutesSinceAttempt > (settings.intervalMinutes() * 3L)) {
                return buildStats(STALE, recentRuns);
            }
        }

        if (consecutiveFailures > 0) {
            return buildStats(WARNING, recentRuns);
        }

        if (lastRun.status() == IngestionRunStatus.COMPLETED && lastRun.articlesFailed() != null && lastRun.articlesFailed() > 0) {
            return buildStats(WARNING, recentRuns);
        }

        if (lastRun.status() == IngestionRunStatus.INTERRUPTED) {
            return buildStats(WARNING, recentRuns);
        }

        return buildStats(HEALTHY, recentRuns);
    }

    private static HealthStats buildStats(String health, List<IngestionRun> recentRuns) {
        if (recentRuns == null || recentRuns.isEmpty()) {
            return new HealthStats(health, null, null, null, null, null, 0, 0, 0, 0, 0);
        }

        IngestionRun lastRun = recentRuns.get(0);
        Instant lastAttemptAt = lastRun.startedAt();
        Instant lastSuccessAt = findLastSuccessAt(recentRuns);
        Instant lastFailureAt = findLastFailureAt(recentRuns);
        
        int consecutiveFailures = countConsecutiveTerminalFailures(recentRuns);

        return new HealthStats(
                health,
                lastAttemptAt,
                lastSuccessAt,
                lastFailureAt,
                lastRun.status().name(),
                lastRun.id(),
                lastRun.articlesDiscovered() != null ? lastRun.articlesDiscovered() : 0,
                lastRun.articlesSubmitted() != null ? lastRun.articlesSubmitted() : 0,
                lastRun.articlesSucceeded() != null ? lastRun.articlesSucceeded() : 0,
                lastRun.articlesFailed() != null ? lastRun.articlesFailed() : 0,
                consecutiveFailures
        );
    }

    private static Instant findLastSuccessAt(List<IngestionRun> runs) {
        for (IngestionRun run : runs) {
            if (run.status() == IngestionRunStatus.COMPLETED) {
                return run.startedAt(); // Or finishedAt, but startedAt is consistent
            }
        }
        return null;
    }

    private static Instant findLastFailureAt(List<IngestionRun> runs) {
        for (IngestionRun run : runs) {
            if (run.status() == IngestionRunStatus.FAILED) {
                return run.startedAt();
            }
        }
        return null;
    }

    private static int countConsecutiveTerminalFailures(List<IngestionRun> runs) {
        int count = 0;
        for (IngestionRun run : runs) {
            if (run.status() == IngestionRunStatus.FAILED) {
                count++;
            } else if (run.status() == IngestionRunStatus.COMPLETED) {
                break;
            }
            // RUNNING, INTERRUPTED do not break the chain, nor do they add to it
        }
        return count;
    }

    public record HealthStats(
            String health,
            Instant lastAttemptAt,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            String lastRunStatus,
            String lastRunId,
            Integer lastDiscovered,
            Integer lastSubmitted,
            Integer lastSucceeded,
            Integer lastFailed,
            int consecutiveFailures
    ) {
    }
}
