/*
 * File overview: Defines and validates input accepted by Ask Story operations.
 * Area: story clustering, timelines, comparison, and Q&A.
 */
package lk.srilankannews.story.ask;

import jakarta.validation.constraints.NotBlank;
import lk.srilankannews.common.domain.Language;

public record AskStoryRequest(@NotBlank String question, Language displayLanguage) {
}
