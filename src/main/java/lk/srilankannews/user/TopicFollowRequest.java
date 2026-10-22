/*
 * File overview: Defines and validates input accepted by Topic Follow operations.
 * Area: reader preferences and personalization.
 */
package lk.srilankannews.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TopicFollowRequest(
        @NotBlank @Size(max = 120) String topic
) {
}
