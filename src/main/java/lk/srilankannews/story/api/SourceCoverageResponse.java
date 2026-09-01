package lk.srilankannews.story.api;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.common.domain.Language;

public record SourceCoverageResponse(
        CoverageSourceResponse source,
        int reportCount,
        List<Language> languages,
        Instant firstPublishedAt,
        Instant lastPublishedAt,
        List<CoverageArticleResponse> articles,
        List<String> topics,
        List<String> uniqueTopics,
        List<CoverageEntityResponse> entities,
        List<CoverageEntityResponse> uniqueEntities
) {
    public SourceCoverageResponse {
        languages = List.copyOf(languages);
        articles = List.copyOf(articles);
        topics = List.copyOf(topics);
        uniqueTopics = List.copyOf(uniqueTopics);
        entities = List.copyOf(entities);
        uniqueEntities = List.copyOf(uniqueEntities);
    }
}
