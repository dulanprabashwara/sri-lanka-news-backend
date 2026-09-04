package lk.srilankannews.story;

import lk.srilankannews.article.MediaType;

public record StoryRepresentativeMedia(
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
