package lk.srilankannews.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class UserPreferencesBackfillRunnerTest {

    @Mock
    private MongoOperations mongoOperations;

    @Mock
    private UserPreferencesService preferencesService;

    @Test
    void backfillsPreferencesForDiscoveredUsers() {
        when(mongoOperations.findDistinct(any(Query.class), eq("userId"), eq("user_bookmarks"), eq(String.class)))
                .thenReturn(List.of("user-1"));
        when(mongoOperations.findDistinct(any(Query.class), eq("userId"), eq("user_follows"), eq(String.class)))
                .thenReturn(List.of("user-2"));
        when(mongoOperations.findDistinct(any(Query.class), eq("_id"), eq("user_notification_preferences"), eq(String.class)))
                .thenReturn(List.of("user-1", "user-2"));

        UserPreferencesBackfillRunner runner = new UserPreferencesBackfillRunner(mongoOperations, preferencesService);
        runner.run(null);

        verify(preferencesService).get("user-1");
        verify(preferencesService).get("user-2");
    }
}
