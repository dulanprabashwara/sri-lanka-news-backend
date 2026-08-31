package lk.srilankannews.article.api;

import java.util.List;
import lk.srilankannews.article.Article;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.api.SourceApiMapper;
import org.springframework.stereotype.Component;

@Component
public class ArticleApiMapper {

    private final SourceApiMapper sourceApiMapper;

    public ArticleApiMapper(SourceApiMapper sourceApiMapper) {
        this.sourceApiMapper = sourceApiMapper;
    }

    public ArticleResponse toResponse(Article article, Source source) {
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
                sourceApiMapper.toSummary(source));
    }
}
