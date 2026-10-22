/*
 * File overview: Defines and validates input accepted by Admin Ingestion Settings operations.
 * Area: administrative operations and monitoring.
 */
package lk.srilankannews.admin.ingestion;

public record AdminIngestionSettingsRequest(
        boolean enabled,
        int intervalMinutes,
        int jitterSeconds
) {
}
