package lk.srilankannews.story.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record CoverageComparisonResponse(
        String storyId,
        String canonicalTitle,
        int articleCount,
        int sourceCount,
        boolean comparisonAvailable,
        List<String> sharedTopics,
        List<CoverageEntityResponse> sharedEntities,
        List<SourceCoverageResponse> sources,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        LocalizedStoryContentResponse localizedContent
) {
    public CoverageComparisonResponse {
        sharedTopics = List.copyOf(sharedTopics);
        sharedEntities = List.copyOf(sharedEntities);
        sources = List.copyOf(sources);
    }

    public CoverageComparisonResponse(
            String storyId, String canonicalTitle, int articleCount, int sourceCount,
            boolean comparisonAvailable, List<String> sharedTopics,
            List<CoverageEntityResponse> sharedEntities, List<SourceCoverageResponse> sources) {
        this(storyId, canonicalTitle, articleCount, sourceCount, comparisonAvailable,
                sharedTopics, sharedEntities, sources, null);
    }
}
