/*
 * File overview: Defines immutable AI Input data passed between backend components.
 * Area: AI enrichment and embedding integrations.
 */
package lk.srilankannews.ai;

import lk.srilankannews.common.domain.Language;

public record AiInput(String title, String content, Language language) {
}
