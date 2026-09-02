package lk.srilankannews.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminPropertiesTest {

    @Test
    void trimsIgnoresBlanksAndDeduplicatesSubjects() {
        assertThat(new AdminProperties(" user-a, ,user-b,user-a ").parsedUserIds())
                .containsExactlyInAnyOrder("user-a", "user-b");
        assertThat(new AdminProperties("").parsedUserIds()).isEmpty();
        assertThat(new AdminProperties(null).parsedUserIds()).isEmpty();
    }
}
