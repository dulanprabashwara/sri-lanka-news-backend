package lk.srilankannews.ai;

import org.springframework.stereotype.Component;

@Component
public class AiInputPolicy {
    private static final int MIN_CONTENT_CHARACTERS = 50;
    private final GeminiProperties properties;

    public AiInputPolicy(GeminiProperties properties) {
        this.properties = properties;
    }

    public String prepare(String content) {
        if (content == null) {
            throw unusable();
        }
        String prepared = content.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (prepared.length() < MIN_CONTENT_CHARACTERS) {
            throw unusable();
        }
        int limit = properties.maxInputCharacters();
        if (prepared.length() <= limit) {
            return prepared;
        }
        int end = limit;
        if (Character.isHighSurrogate(prepared.charAt(end - 1))) {
            end--;
        }
        return prepared.substring(0, end).stripTrailing();
    }

    private AiProviderException unusable() {
        return new AiProviderException(
                AiProviderException.Kind.UNUSABLE_INPUT,
                "Article content is too short for reliable enrichment");
    }
}
