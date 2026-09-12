package lk.srilankannews.admin;

import java.util.List;

public record AdminAiOverviewResponse(
        EnrichmentCounts enrichment,
        ProviderStatus provider,
        List<ModelProviderInfo> providers
) {
    public AdminAiOverviewResponse(EnrichmentCounts enrichment, ProviderStatus provider) {
        this(enrichment, provider, List.of());
    }

    public record EnrichmentCounts(long completed, long failed, long retrying) {}
    public record ProviderStatus(boolean configured, String providerName, String modelName, String embeddingModelName) {}

    public record ModelProviderInfo(
            String id,
            String name,
            String pipeline,
            String role,
            String model,
            boolean configured,
            String details
    ) {}
}
