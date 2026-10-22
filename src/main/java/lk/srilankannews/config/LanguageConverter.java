/*
 * File overview: Converts external values into the internal Language type.
 * Area: cross-cutting application configuration.
 */
package lk.srilankannews.config;

import lk.srilankannews.common.domain.Language;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class LanguageConverter implements Converter<String, Language> {

    @Override
    public Language convert(String source) {
        return Language.fromCode(source);
    }
}
