package lk.srilankannews.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiInputPolicyTest {

    @Test
    void deterministicallyTruncatesWithoutSplittingSurrogatePairs() {
        AiInputPolicy policy = new AiInputPolicy(new GeminiProperties(
                "key", "model", "v1", Duration.ofSeconds(5), 1000));
        String content = "a".repeat(999) + "\uD83D\uDE00" + "remaining";

        String prepared = policy.prepare(content);

        assertThat(prepared).hasSize(999);
        assertThat(prepared).isEqualTo("a".repeat(999));
    }

    @Test
    void rejectsObviouslyUnusableContent() {
        AiInputPolicy policy = new AiInputPolicy(new GeminiProperties(
                "key", "model", "v1", Duration.ofSeconds(5), 1000));

        assertThatThrownBy(() -> policy.prepare("too short"))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).kind())
                .isEqualTo(AiProviderException.Kind.UNUSABLE_INPUT);
    }
}
