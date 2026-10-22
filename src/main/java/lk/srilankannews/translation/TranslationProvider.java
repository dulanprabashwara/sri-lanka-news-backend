/*
 * File overview: Defines the provider contract for Translation.
 * Area: multilingual translation.
 */
package lk.srilankannews.translation;

import java.util.List;

public interface TranslationProvider {
    List<TranslatedContent> translate(TranslationInput input);
}
