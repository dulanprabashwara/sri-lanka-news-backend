package lk.srilankannews.admin.ingestion;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lk.srilankannews.admin.audit.AdminAuditEventService;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.ingestion.run.IngestionRun;
import lk.srilankannews.ingestion.run.IngestionRunRepository;
import lk.srilankannews.ingestion.run.IngestionRunStatus;
import lk.srilankannews.ingestion.run.IngestionTriggerType;
import lk.srilankannews.ingestion.settings.IngestionSourceSettings;
import lk.srilankannews.ingestion.settings.IngestionSourceSettingsService;
import lk.srilankannews.ingestion.trigger.IngestionTriggerService;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ingestion")
@PreAuthorize("@adminAuthorization.isAdmin(authentication)")
public class AdminIngestionController {

    private final SourceService sourceService;
    private final IngestionSourceSettingsService settingsService;
    private final IngestionRunRepository runRepository;
    private final IngestionTriggerService triggerService;
    private final AdminAuditEventService auditService;
    private final Clock clock;

    public AdminIngestionController(SourceService sourceService,
                                    IngestionSourceSettingsService settingsService,
                                    IngestionRunRepository runRepository,
                                    IngestionTriggerService triggerService,
                                    AdminAuditEventService auditService,
                                    Clock clock) {
        this.sourceService = sourceService;
        this.settingsService = settingsService;
        this.runRepository = runRepository;
        this.triggerService = triggerService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @GetMapping("/sources")
    public ResponseEntity<List<AdminSourceStatusResponse>> getSources() {
        List<Source> sources = sourceService.findAllByName();
        List<IngestionSourceSettings> settingsList = settingsService.findAll();

        List<AdminSourceStatusResponse> response = sources.stream().map(source -> {
            IngestionSourceSettings settings = settingsList.stream()
                    .filter(s -> s.sourceId().equals(source.id()))
                    .findFirst()
                    .orElse(null);

            if (settings == null) {
                // If it hasn't been seeded yet (though it should be)
                return new AdminSourceStatusResponse(
                        source.id(), source.slug(), source.name(), source.defaultLanguage(),
                        false, 10, 120, IngestionHealthCalculator.PAUSED,
                        null, null, null, null, null, null, null, null, null, 0
                );
            }

            List<IngestionRun> recentRuns = runRepository.findTop5BySourceSlugOrderByStartedAtDesc(source.slug());
            IngestionHealthCalculator.HealthStats stats = IngestionHealthCalculator.calculate(settings, recentRuns, Instant.now(clock));

            return new AdminSourceStatusResponse(
                    source.id(),
                    source.slug(),
                    source.name(),
                    source.defaultLanguage(),
                    settings.enabled(),
                    settings.intervalMinutes(),
                    settings.jitterSeconds(),
                    stats.health(),
                    stats.lastAttemptAt(),
                    stats.lastSuccessAt(),
                    stats.lastFailureAt(),
                    stats.lastRunStatus(),
                    stats.lastRunId(),
                    stats.lastDiscovered(),
                    stats.lastSubmitted(),
                    stats.lastSucceeded(),
                    stats.lastFailed(),
                    stats.consecutiveFailures()
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/sources/{sourceSlug}/settings")
    public ResponseEntity<Void> updateSettings(@PathVariable String sourceSlug, @RequestBody AdminIngestionSettingsRequest request, @AuthenticationPrincipal Jwt jwt) {
        String adminUserId = jwt.getSubject();
        settingsService.updateSettings(sourceSlug, request.enabled(), request.intervalMinutes(), request.jitterSeconds(), adminUserId);
        
        auditService.logEvent(adminUserId, "INGESTION_SETTINGS_UPDATED", sourceSlug, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sources/{sourceSlug}/trigger")
    public ResponseEntity<Void> triggerManualRun(@PathVariable String sourceSlug, @AuthenticationPrincipal Jwt jwt) {
        String adminUserId = jwt.getSubject();
        boolean enqueued = triggerService.requestManualTrigger(sourceSlug, adminUserId);
        
        if (enqueued) {
            auditService.logEvent(adminUserId, "INGESTION_RUN_TRIGGERED", sourceSlug, null);
            return ResponseEntity.accepted().build();
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/runs")
    public ResponseEntity<Page<AdminRunHistoryResponse>> getRuns(
            @RequestParam(required = false) String sourceSlug,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType,
            @PageableDefault(sort = "startedAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {

        Page<IngestionRun> page;

        if (sourceSlug != null && status != null && triggerType != null) {
            page = runRepository.findBySourceSlugAndStatusAndTriggerType(sourceSlug, IngestionRunStatus.valueOf(status), IngestionTriggerType.valueOf(triggerType), pageable);
        } else if (sourceSlug != null && status != null) {
            page = runRepository.findBySourceSlugAndStatus(sourceSlug, IngestionRunStatus.valueOf(status), pageable);
        } else if (sourceSlug != null && triggerType != null) {
            page = runRepository.findBySourceSlugAndTriggerType(sourceSlug, IngestionTriggerType.valueOf(triggerType), pageable);
        } else if (status != null && triggerType != null) {
            page = runRepository.findByStatusAndTriggerType(IngestionRunStatus.valueOf(status), IngestionTriggerType.valueOf(triggerType), pageable);
        } else if (sourceSlug != null) {
            page = runRepository.findBySourceSlug(sourceSlug, pageable);
        } else if (status != null) {
            page = runRepository.findByStatus(IngestionRunStatus.valueOf(status), pageable);
        } else if (triggerType != null) {
            page = runRepository.findByTriggerType(IngestionTriggerType.valueOf(triggerType), pageable);
        } else {
            page = runRepository.findAll(pageable);
        }

        return ResponseEntity.ok(page.map(AdminRunHistoryResponse::from));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<AdminRunHistoryResponse> getRun(@PathVariable String runId) {
        return runRepository.findById(runId)
                .map(run -> ResponseEntity.ok(AdminRunHistoryResponse.from(run)))
                .orElseThrow(() -> new ResourceNotFoundException("Run"));
    }
}
