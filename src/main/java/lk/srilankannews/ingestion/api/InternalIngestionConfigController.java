package lk.srilankannews.ingestion.api;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lk.srilankannews.config.IngestionApiKeyAuthenticator;
import lk.srilankannews.ingestion.settings.IngestionSourceSettingsService;
import lk.srilankannews.ingestion.trigger.IngestionTriggerRequest;
import lk.srilankannews.ingestion.trigger.IngestionTriggerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/ingestion")
public class InternalIngestionConfigController {

    private final IngestionSourceSettingsService settingsService;
    private final IngestionTriggerService triggerService;
    private final IngestionApiKeyAuthenticator apiKeyAuthenticator;

    public InternalIngestionConfigController(
            IngestionSourceSettingsService settingsService,
            IngestionTriggerService triggerService,
            IngestionApiKeyAuthenticator apiKeyAuthenticator) {
        this.settingsService = settingsService;
        this.triggerService = triggerService;
        this.apiKeyAuthenticator = apiKeyAuthenticator;
    }

    @GetMapping("/sources")
    public ResponseEntity<List<InternalSourceConfigResponse>> getSources(
            @RequestHeader(value = IngestionApiKeyAuthenticator.HEADER_NAME, required = false) String apiKey) {
        apiKeyAuthenticator.authenticate(apiKey);
        List<InternalSourceConfigResponse> response = settingsService.findAll().stream()
                .map(settings -> new InternalSourceConfigResponse(
                        settings.sourceSlug(),
                        settings.enabled(),
                        settings.intervalMinutes(),
                        settings.jitterSeconds()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/triggers/claim")
    public ResponseEntity<InternalTriggerClaimResponse> claimTrigger(
            @RequestHeader(value = IngestionApiKeyAuthenticator.HEADER_NAME, required = false) String apiKey,
            @RequestBody InternalTriggerClaimRequest request) {
        apiKeyAuthenticator.authenticate(apiKey);
        Optional<IngestionTriggerRequest> claimed = triggerService.claimNextPending(request.workerId());
        if (claimed.isPresent()) {
            return ResponseEntity.ok(new InternalTriggerClaimResponse(true, claimed.get().id(), claimed.get().sourceSlug()));
        }
        return ResponseEntity.ok(new InternalTriggerClaimResponse(false, null, null));
    }

    @PostMapping("/triggers/{id}/run-started")
    public ResponseEntity<Void> runStarted(
            @RequestHeader(value = IngestionApiKeyAuthenticator.HEADER_NAME, required = false) String apiKey,
            @PathVariable String id,
            @RequestBody java.util.Map<String, String> body) {
        apiKeyAuthenticator.authenticate(apiKey);
        triggerService.markRunStarted(id, body.get("runId"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/triggers/{id}/retry")
    public ResponseEntity<Void> retryTrigger(
            @RequestHeader(value = IngestionApiKeyAuthenticator.HEADER_NAME, required = false) String apiKey,
            @PathVariable String id) {
        apiKeyAuthenticator.authenticate(apiKey);
        triggerService.retryLater(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/triggers/{id}/complete")
    public ResponseEntity<Void> completeTrigger(
            @RequestHeader(value = IngestionApiKeyAuthenticator.HEADER_NAME, required = false) String apiKey,
            @PathVariable String id) {
        apiKeyAuthenticator.authenticate(apiKey);
        triggerService.complete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/triggers/{id}/fail")
    public ResponseEntity<Void> failTrigger(
            @RequestHeader(value = IngestionApiKeyAuthenticator.HEADER_NAME, required = false) String apiKey,
            @PathVariable String id) {
        apiKeyAuthenticator.authenticate(apiKey);
        triggerService.fail(id);
        return ResponseEntity.noContent().build();
    }
}
