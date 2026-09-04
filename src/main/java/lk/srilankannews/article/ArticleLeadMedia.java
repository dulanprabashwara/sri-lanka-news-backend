package lk.srilankannews.article;

public record ArticleLeadMedia(
        String url,
        MediaType type,
        String altText,
        String caption,
        String credit,
        Integer width,
        Integer height,
        String discoveredFrom
) {
}
