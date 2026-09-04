package lk.srilankannews.admin;

import java.util.List;

public record AdminOverviewResponse(
        SourceCounts sources,
        ArticleCounts articles,
        StoryCounts stories,
        IngestionCounts ingestion,
        UserCounts users,
        List<AdminArticleResponse> recentFailures) {

    public AdminOverviewResponse {
        recentFailures = List.copyOf(recentFailures);
    }

    public record SourceCounts(long total, long enabled, long paused, long failing) {
    }

    public record ArticleCounts(
            long total, long pending, long processing, long completed,
            long retrying, long failed) {
    }

    public record StoryCounts(long total, long createdRecently, long recentActive) {
    }

    public record IngestionCounts(
            long totalRuns, long completedRuns, long failedRuns, long currentlyRunning, long failingSources) {
    }

    public record UserCounts(
            long totalProfiles, long totalBookmarks, long totalFollows) {
    }
}
