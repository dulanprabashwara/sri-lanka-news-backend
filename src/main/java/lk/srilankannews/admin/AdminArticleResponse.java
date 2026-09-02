package lk.srilankannews.admin;

import java.time.Instant;
import lk.srilankannews.article.ProcessingStatus;

public record AdminArticleResponse(
        String articleId,
        String title,
        AdminSourceSummary source,
        ProcessingStatus processingStatus,
        Instant discoveredAt,
        Instant publishedAt) {
}
