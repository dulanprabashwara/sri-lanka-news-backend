package lk.srilankannews.user;

import java.util.List;
import lk.srilankannews.article.api.ArticleResponse;

public record ForYouItemResponse(
        ArticleResponse article,
        boolean personalized,
        List<RecommendationReasonResponse> reasons) {
    public ForYouItemResponse {
        reasons = List.copyOf(reasons);
    }
}
