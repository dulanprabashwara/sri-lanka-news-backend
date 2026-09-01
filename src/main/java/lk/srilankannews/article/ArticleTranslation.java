package lk.srilankannews.article;

import java.time.Instant;

public record ArticleTranslation(
        String title,
        String summary,
        String model,
        String promptVersion,
        String inputHash,
        Instant translatedAt) {

    public boolean matches(String expectedModel, String expectedPromptVersion, String expectedHash) {
        return model.equals(expectedModel)
                && promptVersion.equals(expectedPromptVersion)
                && inputHash.equals(expectedHash);
    }
}
