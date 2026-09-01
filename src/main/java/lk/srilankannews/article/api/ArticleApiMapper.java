package lk.srilankannews.article.api;

import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.api.SourceApiMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class ArticleApiMapper {

    private final SourceApiMapper sourceApiMapper;
    private final ArticleLocalizationService localizationService;

    @Autowired
    public ArticleApiMapper(
            SourceApiMapper sourceApiMapper,
            ArticleLocalizationService localizationService) {
        this.sourceApiMapper = sourceApiMapper;
        this.localizationService = localizationService;
    }

    public ArticleApiMapper(SourceApiMapper sourceApiMapper) {
        this(sourceApiMapper, null);
    }

    public ArticleResponse toResponse(Article article, Source source) {
        return toResponse(article, source, null);
    }

    public ArticleResponse toResponse(
            Article article, Source source, lk.srilankannews.common.domain.Language displayLanguage) {
        String summary = article.aiEnrichment() == null ? null : article.aiEnrichment().summary();
        List<String> topics = article.aiEnrichment() == null
                ? List.of()
                : article.aiEnrichment().topics();
        return new ArticleResponse(
                article.id(),
                article.title(),
                article.originalUrl(),
                article.originalLanguage(),
                article.authors(),
                article.publishedAt(),
                article.discoveredAt(),
                article.category(),
                summary,
                topics,
                sourceApiMapper.toSummary(source),
                displayLanguage == null ? null : localizationService.localize(article, displayLanguage));
    }
}
