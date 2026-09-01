package lk.srilankannews.article.api;

import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleTranslation;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.translation.ArticleTranslationService;
import org.springframework.stereotype.Service;

@Service
public class ArticleLocalizationService {
    private final ArticleTranslationService translationService;

    public ArticleLocalizationService(ArticleTranslationService translationService) {
        this.translationService = translationService;
    }

    public LocalizedContentResponse localize(Article article, Language requested) {
        if (requested == null) {
            return null;
        }
        String originalSummary = article.aiEnrichment() == null
                ? null
                : article.aiEnrichment().summary();
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
