package lk.srilankannews.source.api;

import lk.srilankannews.source.Source;
import org.springframework.stereotype.Component;

@Component
public class SourceApiMapper {

    public SourceResponse toResponse(Source source) {
        return new SourceResponse(
                source.name(),
                source.slug(),
                source.baseUrl(),
                source.defaultLanguage());
    }

    public SourceSummaryResponse toSummary(Source source) {
        return new SourceSummaryResponse(
                source.name(),
                source.slug(),
                source.baseUrl());
    }
}
