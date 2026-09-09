package lk.srilankannews.article;

import java.time.Instant;

public record ArticleTranslation(
        String title,
        String summary,
        String model,
        String promptVersion,
        String inputHash,
        Instant translatedAt,
        String provider) {

    public ArticleTranslation(
            String title,
            String summary,
            String model,
            String promptVersion,
            String inputHash,
            Instant translatedAt) {
        this(title, summary, model, promptVersion, inputHash, translatedAt, null);
    }

    public boolean matches(String expectedModel, String expectedPromptVersion, String expectedHash) {
        boolean providerModelMatches = "AZURE_TRANSLATOR".equals(provider)
                || expectedModel.equals(model);
        return providerModelMatches
                && expectedPromptVersion.equals(promptVersion)
                && expectedHash.equals(inputHash);
    }
}
