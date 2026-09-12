package lk.srilankannews.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import lk.srilankannews.article.ArticleCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {
    @Mock UserPreferencesRepository repository;
    @Mock MongoOperations mongoOperations;
    private UserPreferencesService service;
    private final Instant now = Instant.parse("2026-09-02T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new UserPreferencesService(repository, mongoOperations,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void initializesAndPersistsDefaultsWhenPreferencesDoNotExist() {
        when(repository.findByUserId("user-a")).thenReturn(Optional.empty());
        UserPreferences saved = new UserPreferences("id", "user-a", DisplayLanguagePreference.ORIGINAL,
                Set.of(), true, now, now);
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(UserPreferences.class))).thenReturn(saved);

        UserPreferencesResponse response = service.get("user-a");
        assertThat(response.preferredDisplayLanguage()).isEqualTo(DisplayLanguagePreference.ORIGINAL);
        assertThat(response.preferredCategories()).isEmpty();
        assertThat(response.createdAt()).isEqualTo(now);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoOperations).findAndModify(query.capture(), update.capture(),
                any(FindAndModifyOptions.class), eq(UserPreferences.class));
        assertThat(query.getValue().getQueryObject().getString("userId")).isEqualTo("user-a");
        assertThat(update.getValue().getUpdateObject().get("$setOnInsert").toString())
                .contains("userId=user-a")
                .contains("preferredDisplayLanguage=ORIGINAL");
    }

    @Test
    void returnsExistingPreferencesWithoutReinitializing() {
        UserPreferences existing = new UserPreferences("id", "user-a", DisplayLanguagePreference.SI,
                Set.of(ArticleCategory.POLITICS), false, now, now);
        when(repository.findByUserId("user-a")).thenReturn(Optional.of(existing));

        UserPreferencesResponse response = service.get("user-a");
        assertThat(response.preferredDisplayLanguage()).isEqualTo(DisplayLanguagePreference.SI);
        assertThat(response.preferredCategories()).containsExactly(ArticleCategory.POLITICS);
        assertThat(response.analyticsEnabled()).isFalse();

        org.mockito.Mockito.verifyNoInteractions(mongoOperations);
    }

    @Test
    void atomicallyUpsertsOwnerScopedPreferencesAndSortsCategories() {
        UserPreferences saved = new UserPreferences("id", "user-a", DisplayLanguagePreference.SI,
                Set.of(ArticleCategory.SPORTS, ArticleCategory.BUSINESS), true, now, now);
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(UserPreferences.class))).thenReturn(saved);

        UserPreferencesResponse response = service.update("user-a", new UserPreferencesRequest(
                DisplayLanguagePreference.SI,
                Set.of(ArticleCategory.SPORTS, ArticleCategory.BUSINESS), true));

        assertThat(response.preferredCategories())
                .containsExactly(ArticleCategory.BUSINESS, ArticleCategory.SPORTS);
        verify(mongoOperations).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(UserPreferences.class));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoOperations).findAndModify(query.capture(), update.capture(),
                any(FindAndModifyOptions.class), eq(UserPreferences.class));
        assertThat(query.getValue().getQueryObject().getString("userId")).isEqualTo("user-a");
        assertThat(update.getValue().getUpdateObject().get("$setOnInsert").toString())
                .contains("userId=user-a");
    }

    @Test
    void readsOnlyTheAuthenticatedOwnersPreferences() {
        service.get("user-b");
        verify(repository).findByUserId("user-b");
    }

    @Test
    void concurrentFirstGetResolvesToOneOwnerDocument() {
        when(repository.findByUserId("user-a")).thenReturn(Optional.empty());
        UserPreferences saved = new UserPreferences("id", "user-a", DisplayLanguagePreference.ORIGINAL,
                Set.of(), true, now, now);
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(UserPreferences.class)))
                .thenThrow(new DuplicateKeyException("concurrent preference create"))
                .thenReturn(saved);

        UserPreferencesResponse response = service.get("user-a");
        assertThat(response.preferredDisplayLanguage()).isEqualTo(DisplayLanguagePreference.ORIGINAL);
        assertThat(response.preferredCategories()).isEmpty();
        verify(mongoOperations, org.mockito.Mockito.times(2)).findAndModify(any(Query.class),
                any(Update.class), any(FindAndModifyOptions.class), eq(UserPreferences.class));
    }

    @Test
    void concurrentFirstCreatesResolveToOneOwnerDocument() {
        UserPreferences saved = new UserPreferences("id", "user-a", DisplayLanguagePreference.EN,
                Set.of(), true, now, now);
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(UserPreferences.class)))
                .thenThrow(new DuplicateKeyException("concurrent preference create"))
                .thenReturn(saved);

        assertThat(service.update("user-a", new UserPreferencesRequest(
                DisplayLanguagePreference.EN, Set.of(), true)).preferredDisplayLanguage())
                .isEqualTo(DisplayLanguagePreference.EN);
        verify(mongoOperations, org.mockito.Mockito.times(2)).findAndModify(any(Query.class),
                any(Update.class), any(FindAndModifyOptions.class), eq(UserPreferences.class));
    }
}
