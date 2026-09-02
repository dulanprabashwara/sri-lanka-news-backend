package lk.srilankannews.story.ask;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("news.story.ask")
public record AskStoryProperties(
        @NotBlank String promptVersion,
        @Min(3) @Max(2000) int maxQuestionCharacters,
        @Min(1) @Max(10) int maxRetrievedArticles,
        @Min(500) @Max(20000) int maxArticleContextCharacters,
        @Min(1000) @Max(100000) int maxTotalContextCharacters,
        @Min(100) @Max(10000) int maxAnswerCharacters) {
}
