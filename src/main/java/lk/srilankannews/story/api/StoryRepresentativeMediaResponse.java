package lk.srilankannews.story.api;

import lk.srilankannews.article.MediaType;

public record StoryRepresentativeMediaResponse(
        String url,
        MediaType type,
        String altText,
        String caption,
        String credit,
        Integer width,
        Integer height,
        String articleId,
        String source
) {
}
