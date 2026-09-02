package lk.srilankannews.admin;

import java.util.List;

public record AdminOverviewResponse(
        SourceCounts sources,
        ArticleCounts articles,
        StoryCounts stories,
        List<AdminArticleResponse> recentFailures) {

    public AdminOverviewResponse {
        recentFailures = List.copyOf(recentFailures);
    }

    public record SourceCounts(long total) {
    }

    public record ArticleCounts(
            long total, long pending, long processing, long completed,
            long retrying, long failed) {
    }

    public record StoryCounts(long total) {
    }
}
