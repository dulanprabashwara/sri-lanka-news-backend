package lk.srilankannews.ingestion.settings;

import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lk.srilankannews.source.SourceService;
import org.springframework.stereotype.Service;

@Service
public class IngestionSourceSettingsService {

    private final IngestionSourceSettingsRepository repository;
    private final SourceService sourceService;
    private final Clock clock;

    public IngestionSourceSettingsService(IngestionSourceSettingsRepository repository, SourceService sourceService, Clock clock) {
        this.repository = repository;
        this.sourceService = sourceService;
        this.clock = clock;
    }

    @PostConstruct
    public void seedDefaultSettings() {
        Instant now = Instant.now(clock);
        
        seedSource("daily-mirror", true, 10, 120, now);
        seedSource("newsfirst", true, 10, 120, now);
        seedSource("hiru-news-sinhala", false, 10, 120, now);
        seedSource("lakbima-news", true, 15, 120, now);
        seedSource("newswire", true, 10, 120, now);

        // Phase 31 Sources
        seedSource("the-island", true, 15, 120, now);
        seedSource("lankadeepa", true, 15, 120, now);
        seedSource("divaina", true, 15, 120, now);

        seedSource("daily-news", false, 15, 120, now);
        seedSource("virakesari", false, 15, 120, now);
        seedSource("thinakaran", false, 15, 120, now);
    }

    private void seedSource(String slug, boolean enabled, int interval, int jitter, Instant now) {
        sourceService.findBySlug(slug).ifPresent(source -> {
            if (repository.findBySourceSlug(slug).isEmpty()) {
                repository.save(new IngestionSourceSettings(
                        null,
                        source.id(),
                        slug,
                        enabled,
                        interval,
                        jitter,
                        now,
                        now,
                        "SYSTEM"
                ));
            }
        });
    }

    public void ensureDefaults(String slug, boolean enabled, int interval, int jitter) {
        seedSource(slug, enabled, interval, jitter, Instant.now(clock));
    }

    public void disableIfPresent(String slug) {
        repository.findBySourceSlug(slug).filter(IngestionSourceSettings::enabled).ifPresent(settings ->
                repository.save(settings.withUpdates(
                        false,
                        settings.intervalMinutes(),
                        settings.jitterSeconds(),
                        "SYSTEM",
                        Instant.now(clock))));
    }

    public boolean isEnabled(String slug) {
        return repository.findBySourceSlug(slug)
                .map(IngestionSourceSettings::enabled)
                .orElse(false);
    }

    public List<IngestionSourceSettings> findAll() {
        return repository.findAll();
    }

    public IngestionSourceSettings updateSettings(String sourceSlug, boolean enabled, int intervalMinutes, int jitterSeconds, String adminUserId) {
        if (intervalMinutes < 5 || intervalMinutes > 1440) {
            throw new IllegalArgumentException("Interval must be between 5 and 1440 minutes");
        }
        
        int maxJitter = Math.min(300, (intervalMinutes * 60) - 1);
        if (jitterSeconds < 0 || jitterSeconds > maxJitter) {
            throw new IllegalArgumentException("Jitter must be between 0 and " + maxJitter + " seconds");
        }

        IngestionSourceSettings settings = repository.findBySourceSlug(sourceSlug)
                .orElseThrow(() -> new IllegalArgumentException("Settings not found for source " + sourceSlug));

        IngestionSourceSettings updated = settings.withUpdates(
                enabled, intervalMinutes, jitterSeconds, adminUserId, Instant.now(clock)
        );

        return repository.save(updated);
    }
}
