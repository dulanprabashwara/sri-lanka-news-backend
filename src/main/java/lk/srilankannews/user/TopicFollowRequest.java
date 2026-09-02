package lk.srilankannews.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TopicFollowRequest(
        @NotBlank @Size(max = 120) String topic
) {
}
