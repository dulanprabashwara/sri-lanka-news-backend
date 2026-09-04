package lk.srilankannews.admin;

public record AdminAiOverviewResponse(
        EnrichmentCounts enrichment,
        ProviderStatus provider
) {
    public record EnrichmentCounts(long completed, long failed, long retrying) {}
    public record ProviderStatus(boolean configured, String providerName, String modelName, String embeddingModelName) {}
}
