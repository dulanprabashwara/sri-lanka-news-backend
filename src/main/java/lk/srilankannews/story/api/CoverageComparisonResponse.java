package lk.srilankannews.story.api;

import java.util.List;

public record CoverageComparisonResponse(
        String storyId,
        String canonicalTitle,
        int articleCount,
        int sourceCount,
        boolean comparisonAvailable,
        List<String> sharedTopics,
        List<CoverageEntityResponse> sharedEntities,
        List<SourceCoverageResponse> sources
) {
    public CoverageComparisonResponse {
        sharedTopics = List.copyOf(sharedTopics);
        sharedEntities = List.copyOf(sharedEntities);
        sources = List.copyOf(sources);
    }
}
