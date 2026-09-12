package lk.srilankannews.admin;

import java.util.ArrayList;
import java.util.List;
import lk.srilankannews.ai.GeminiProperties;
import lk.srilankannews.ai.openrouter.OpenRouterProperties;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.story.StoryEmbeddingProperties;
import lk.srilankannews.story.ask.AskStoryProperties;
import lk.srilankannews.translation.AzureTranslatorProperties;
import lk.srilankannews.translation.TranslationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai")
@PreAuthorize("@adminAuthorization.isAdmin(authentication)")
public class AdminAiController {

    private final AdminMongoOperations operations;
    private final GeminiProperties geminiProperties;
    private final OpenRouterProperties openRouterProperties;
    private final TranslationProperties translationProperties;
    private final AzureTranslatorProperties azureProperties;
    private final StoryEmbeddingProperties storyEmbeddingProperties;
    private final AskStoryProperties askStoryProperties;

    public AdminAiController(
            AdminMongoOperations operations,
            @Autowired(required = false) GeminiProperties geminiProperties,
            @Autowired(required = false) OpenRouterProperties openRouterProperties,
            @Autowired(required = false) TranslationProperties translationProperties,
            @Autowired(required = false) AzureTranslatorProperties azureProperties,
            @Autowired(required = false) StoryEmbeddingProperties storyEmbeddingProperties,
            @Autowired(required = false) AskStoryProperties askStoryProperties) {
        this.operations = operations;
        this.geminiProperties = geminiProperties;
        this.openRouterProperties = openRouterProperties;
        this.translationProperties = translationProperties;
        this.azureProperties = azureProperties;
        this.storyEmbeddingProperties = storyEmbeddingProperties;
        this.askStoryProperties = askStoryProperties;
    }

    @GetMapping
    public AdminAiOverviewResponse overview() {
        String geminiModel = geminiProperties != null ? geminiProperties.model() : "gemini-3.6-flash";
        String embeddingModel = storyEmbeddingProperties != null ? storyEmbeddingProperties.model() : "gemini-embedding-2";
        boolean geminiConfigured = geminiProperties != null && geminiProperties.apiKey() != null && !geminiProperties.apiKey().isBlank();

        List<AdminAiOverviewResponse.ModelProviderInfo> providers = new ArrayList<>();

        // 1. Enrichment - Primary (Gemini)
        providers.add(new AdminAiOverviewResponse.ModelProviderInfo(
                "gemini-enrichment",
                "Google Gemini",
                "ENRICHMENT",
                "PRIMARY",
                geminiModel,
                geminiConfigured,
                geminiProperties != null ? "Prompt v" + geminiProperties.promptVersion() : "Default prompt"
        ));

        // 2. Enrichment - Fallback (OpenRouter)
        boolean openRouterConfigured = openRouterProperties != null && openRouterProperties.configured();
        String openRouterModel = openRouterProperties != null ? openRouterProperties.model() : "openrouter/free";
        providers.add(new AdminAiOverviewResponse.ModelProviderInfo(
                "openrouter-enrichment",
                "OpenRouter AI",
                "ENRICHMENT",
                "FALLBACK",
                openRouterModel,
                openRouterConfigured,
                openRouterProperties != null && openRouterProperties.baseUrl() != null
                        ? openRouterProperties.baseUrl()
                        : "https://openrouter.ai/api/v1"
        ));

        // 3. Translation - Primary (Gemini Multilingual)
        String translationModel = translationProperties != null ? translationProperties.model() : geminiModel;
        providers.add(new AdminAiOverviewResponse.ModelProviderInfo(
                "gemini-translation",
                "Google Gemini Multilingual",
                "TRANSLATION",
                "PRIMARY",
                translationModel,
                geminiConfigured,
                translationProperties != null ? "Prompt v" + translationProperties.promptVersion() : "Default prompt"
        ));

        // 4. Translation - Fallback (Microsoft Azure AI Translator)
        boolean azureConfigured = azureProperties != null && azureProperties.configured();
        String azureRegion = azureProperties != null && azureProperties.region() != null ? azureProperties.region() : "Global";
        providers.add(new AdminAiOverviewResponse.ModelProviderInfo(
                "azure-translation",
                "Microsoft Azure AI Translator",
                "TRANSLATION",
                "FALLBACK",
                "text-translation-v3",
                azureConfigured,
                "Region: " + azureRegion
        ));

        // 5. Story Embedding - Primary (Gemini Embedding)
        int dimensions = storyEmbeddingProperties != null ? storyEmbeddingProperties.dimensions() : 768;
        providers.add(new AdminAiOverviewResponse.ModelProviderInfo(
                "gemini-embedding",
                "Google Gemini Embeddings",
                "EMBEDDING",
                "PRIMARY",
                embeddingModel,
                geminiConfigured,
                dimensions + " dimensions"
        ));

        // 6. Grounded Q&A - Primary (Gemini)
        providers.add(new AdminAiOverviewResponse.ModelProviderInfo(
                "gemini-ask-story",
                "Google Gemini Grounded Q&A",
                "GROUNDED_QA",
                "PRIMARY",
                geminiModel,
                geminiConfigured,
                askStoryProperties != null ? "Prompt v" + askStoryProperties.promptVersion() : "Default prompt"
        ));

        // 7. Grounded Q&A - Fallback (OpenRouter)
        providers.add(new AdminAiOverviewResponse.ModelProviderInfo(
                "openrouter-ask-story",
                "OpenRouter Grounded Q&A",
                "GROUNDED_QA",
                "FALLBACK",
                openRouterModel,
                openRouterConfigured,
                openRouterProperties != null && openRouterProperties.baseUrl() != null
                        ? openRouterProperties.baseUrl()
                        : "https://openrouter.ai/api/v1"
        ));

        return new AdminAiOverviewResponse(
                new AdminAiOverviewResponse.EnrichmentCounts(
                        operations.articleCount(ProcessingStatus.COMPLETED),
                        operations.articleCount(ProcessingStatus.FAILED),
                        operations.articleCount(ProcessingStatus.RETRYING)
                ),
                new AdminAiOverviewResponse.ProviderStatus(
                        geminiConfigured, "Gemini Provider", geminiModel, embeddingModel
                ),
                providers
        );
    }
}

