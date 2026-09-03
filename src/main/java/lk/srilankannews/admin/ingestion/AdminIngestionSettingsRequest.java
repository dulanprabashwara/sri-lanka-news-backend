package lk.srilankannews.admin.ingestion;

public record AdminIngestionSettingsRequest(
        boolean enabled,
        int intervalMinutes,
        int jitterSeconds
) {
}
