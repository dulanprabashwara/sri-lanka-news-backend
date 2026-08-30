package lk.srilankannews.config;

import java.util.Locale;
import lk.srilankannews.article.ArticleCategory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ArticleCategoryConverter implements Converter<String, ArticleCategory> {

    @Override
    public ArticleCategory convert(String source) {
        return ArticleCategory.valueOf(source.toUpperCase(Locale.ROOT));
    }
}
