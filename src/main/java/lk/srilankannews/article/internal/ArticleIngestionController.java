package lk.srilankannews.article.internal;

import jakarta.validation.Valid;
import lk.srilankannews.config.IngestionApiKeyAuthenticator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/articles")
public class ArticleIngestionController {

    private final ArticleIngestionService articleIngestionService;
    private final IngestionApiKeyAuthenticator apiKeyAuthenticator;

    public ArticleIngestionController(
            ArticleIngestionService articleIngestionService,
            IngestionApiKeyAuthenticator apiKeyAuthenticator
    ) {
        this.articleIngestionService = articleIngestionService;
        this.apiKeyAuthenticator = apiKeyAuthenticator;
    }

    @PostMapping
    public ResponseEntity<ArticleIngestionResponse> ingest(
            @RequestHeader(value = IngestionApiKeyAuthenticator.HEADER_NAME, required = false)
            String apiKey,
            @Valid @RequestBody ArticleIngestionRequest request
    ) {
        apiKeyAuthenticator.authenticate(apiKey);
        ArticleIngestionResponse response = articleIngestionService.ingest(request);
        HttpStatus status = response.status() == ArticleIngestionResponse.Status.CREATED
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
