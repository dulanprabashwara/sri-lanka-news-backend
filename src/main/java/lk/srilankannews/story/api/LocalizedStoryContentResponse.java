/*
 * File overview: Defines data returned by Localized Story Content API operations.
 * Area: story clustering, timelines, comparison, and Q&A.
 */
package lk.srilankannews.story.api;

import lk.srilankannews.common.domain.Language;

public record LocalizedStoryContentResponse(
        Language requestedLanguage,
        Language resolvedLanguage,
        boolean translated,
        boolean fallback,
        String title) {
}
