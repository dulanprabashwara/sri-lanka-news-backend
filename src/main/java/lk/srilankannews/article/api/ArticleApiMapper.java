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
        String aiSummary = article.aiEnrichment() == null ? null : article.aiEnrichment().summary();
        String summary = article.summary() != null ? article.summary() : aiSummary;
        List<String> topics = article.aiEnrichment() == null
                ? List.of()
                : article.aiEnrichment().topics();
        ArticleLeadMediaResponse leadMediaResponse = article.leadMedia() == null ? null : new ArticleLeadMediaResponse(
                article.leadMedia().url(),
                article.leadMedia().type(),
                article.leadMedia().altText(),
                article.leadMedia().caption(),
                article.leadMedia().credit(),
                article.leadMedia().width(),
                article.leadMedia().height()
        );
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
                leadMediaResponse,
                displayLanguage == null ? null : localizationService.localize(article, displayLanguage));
    }
}
