package lk.srilankannews.user;

import java.util.List;

public record ForYouFeedResponse(
        List<ForYouItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        PersonalizationResponse personalization) {
    public ForYouFeedResponse {
        content = List.copyOf(content);
    }
}
