package lk.srilankannews.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FollowBatchStatusRequest(
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 100) String> sourceSlugs,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 120) String> topics
) {
    public FollowBatchStatusRequest {
        sourceSlugs = sourceSlugs == null ? null : List.copyOf(sourceSlugs);
        topics = topics == null ? null : List.copyOf(topics);
    }
}
