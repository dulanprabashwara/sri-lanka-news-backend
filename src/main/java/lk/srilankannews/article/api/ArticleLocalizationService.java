package lk.srilankannews.article.api;

import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleTranslation;
import lk.srilankannews.article.ArticleSummaryResolver;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.translation.ArticleTranslationService;
import org.springframework.stereotype.Service;

@Service
public class ArticleLocalizationService {
    private final ArticleTranslationService translationService;
    private final ArticleSummaryResolver summaryResolver;

    public ArticleLocalizationService(
            ArticleTranslationService translationService,
            ArticleSummaryResolver summaryResolver) {
        this.translationService = translationService;
        this.summaryResolver = summaryResolver;
    }

    public LocalizedContentResponse localize(Article article, Language requested) {
        if (requested == null) {
            return null;
        }
        ArticleSummaryResolver.ResolvedSummary resolvedSummary = summaryResolver.resolve(article);
        String originalSummary = resolvedSummary == null ? null : resolvedSummary.text();
        if (requested == article.originalLanguage()) {
            return new LocalizedContentResponse(
                    requested, requested, false, false, article.title(), originalSummary);
        }
        ArticleTranslation translation = article.translations().get(requested);
        if (translation != null && translationService.valid(article, requested)) {
            return new LocalizedContentResponse(
                    requested, requested, true, false, translation.title(), translation.summary());
        }
        return new LocalizedContentResponse(
                requested, article.originalLanguage(), false, true, article.title(), originalSummary);
    }
}
