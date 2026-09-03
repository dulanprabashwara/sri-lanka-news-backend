package lk.srilankannews.ingestion.run;

import jakarta.validation.Valid;
import java.util.Map;
import lk.srilankannews.config.IngestionApiKeyAuthenticator;
import lk.srilankannews.ingestion.run.api.ClaimRequest;
import lk.srilankannews.ingestion.run.api.ClaimResponse;
import lk.srilankannews.ingestion.run.api.CompleteRequest;
import lk.srilankannews.ingestion.run.api.FailRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/ingestion-runs")
public class IngestionRunController {

    private final IngestionRunService runService;
    private final IngestionApiKeyAuthenticator apiKeyAuthenticator;

    public IngestionRunController(
            IngestionRunService runService,
            IngestionApiKeyAuthenticator apiKeyAuthenticator
    ) {
        this.runService = runService;
        this.apiKeyAuthenticator = apiKeyAuthenticator;
    }

    @PostMapping("/claim")
    public ResponseEntity<ClaimResponse> claim(
            @RequestHeader(value = IngestionApiKeyAuthenticator.HEADER_NAME, required = false) String apiKey,
            @Valid @RequestBody ClaimRequest request
    ) {
        apiKeyAuthenticator.authenticate(apiKey);
        ClaimResponse response = runService.claim(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{runId}/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(
            @RequestHeader(value = IngestionApiKeyAuthenticator.HEADER_NAME, required = false) String apiKey,
            @PathVariable String runId
    ) {
        apiKeyAuthenticator.authenticate(apiKey);
        var newExpiresAt = runService.heartbeat(runId);
        return ResponseEntity.ok(Map.of("leaseExpiresAt", newExpiresAt));
    }

    @PostMapping("/{runId}/complete")
    public ResponseEntity<Void> complete(
            @RequestHeader(value = IngestionApiKeyAuthenticator.HEADER_NAME, required = false) String apiKey,
            @PathVariable String runId,
            @Valid @RequestBody CompleteRequest request
    ) {
        apiKeyAuthenticator.authenticate(apiKey);
        runService.complete(runId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{runId}/fail")
    public ResponseEntity<Void> fail(
            @RequestHeader(value = IngestionApiKeyAuthenticator.HEADER_NAME, required = false) String apiKey,
            @PathVariable String runId,
            @Valid @RequestBody FailRequest request
    ) {
        apiKeyAuthenticator.authenticate(apiKey);
        runService.fail(runId, request);
        return ResponseEntity.noContent().build();
    }
}
