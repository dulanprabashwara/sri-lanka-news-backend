package lk.srilankannews.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.ProcessingStatus;
import lk.srilankannews.article.api.ArticleLocalizationService;
import lk.srilankannews.article.api.LocalizedContentResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryRepository;
import lk.srilankannews.user.DisplayLanguagePreference;
import lk.srilankannews.user.UserPreferencesResponse;
import lk.srilankannews.user.UserPreferencesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NotificationLocalizationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private StoryRepository storyRepository;
    @Mock private ArticleLocalizationService articleLocalizationService;
    @Mock private UserPreferencesService userPreferencesService;

    private NotificationLocalizationService service;
    private final Instant now = Instant.parse("2026-09-12T10:00:00Z");

    @BeforeEach
    void setUp() {
        service = new NotificationLocalizationService(
                notificationRepository, articleRepository, storyRepository,
                articleLocalizationService, userPreferencesService);
    }

    @Test
    void getLocalizedNotifications_whenNoNotifications_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("user1", pageable))
                .thenReturn(Page.empty());

        Page<NotificationResponse> result = service.getLocalizedNotifications("user1", Language.EN, pageable);

        assertThat(result).isEmpty();
        verifyNoInteractions(articleRepository, storyRepository, articleLocalizationService);
    }

    @Test
    void getLocalizedNotifications_whenNoLanguageRequestedAndPreferenceIsOriginal_returnsUnlocalized() {
        Pageable pageable = PageRequest.of(0, 10);
        Notification notification = createNotification("n1", "user1", "art1", null, "Sinhala Title", "Sinhala Msg");
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("user1", pageable))
                .thenReturn(new PageImpl<>(List.of(notification)));
        when(userPreferencesService.get("user1")).thenReturn(
                new UserPreferencesResponse(DisplayLanguagePreference.ORIGINAL, List.of(), true, now, now));

        Page<NotificationResponse> result = service.getLocalizedNotifications("user1", null, pageable);

        assertThat(result.getContent()).hasSize(1);
        NotificationResponse response = result.getContent().get(0);
        assertThat(response.title()).isEqualTo("Sinhala Title");
        assertThat(response.message()).isEqualTo("Sinhala Msg");
        assertThat(response.localizedContent()).isNull();
        verifyNoInteractions(articleRepository, storyRepository, articleLocalizationService);
    }

    @Test
    void getLocalizedNotifications_withExplicitLanguage_localizesTriggeringArticle() {
        Pageable pageable = PageRequest.of(0, 10);
        Notification notification = createNotification("n1", "user1", "art1", "story1", "Sinhala Title", "Sinhala Msg");
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("user1", pageable))
                .thenReturn(new PageImpl<>(List.of(notification)));

        Article article = createArticle("art1", Language.SI, "Sinhala Title");
        when(articleRepository.findAllById(Set.of("art1"))).thenReturn(List.of(article));
        when(articleLocalizationService.localize(article, Language.EN)).thenReturn(
                new LocalizedContentResponse(Language.EN, Language.EN, true, false, "English Title", "English Summary"));

        Page<NotificationResponse> result = service.getLocalizedNotifications("user1", Language.EN, pageable);

        assertThat(result.getContent()).hasSize(1);
        NotificationResponse response = result.getContent().get(0);
        assertThat(response.title()).isEqualTo("English Title");
        assertThat(response.message()).isEqualTo("English Summary");
        assertThat(response.localizedContent()).isNotNull();
        assertThat(response.localizedContent().translated()).isTrue();
        assertThat(response.localizedContent().title()).isEqualTo("English Title");
        assertThat(response.localizedContent().summary()).isEqualTo("English Summary");
    }

    @Test
    void getLocalizedNotifications_whenTriggeringArticleIdNull_looksUpStoryRepresentativeArticle() {
        Pageable pageable = PageRequest.of(0, 10);
        Notification notification = createNotification("n1", "user1", null, "story1", "Sinhala Story", "Sinhala Msg");
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("user1", pageable))
                .thenReturn(new PageImpl<>(List.of(notification)));

        Story story = new Story("story1", "Sinhala Story", "art2", ArticleCategory.LOCAL, now, now, 2, Set.of("s1"), Set.of("art2"), now, now, "v1");
        when(storyRepository.findAllById(Set.of("story1"))).thenReturn(List.of(story));

        Article article = createArticle("art2", Language.SI, "Sinhala Title");
        when(articleRepository.findAllById(Set.of("art2"))).thenReturn(List.of(article));
        when(articleLocalizationService.localize(article, Language.TA)).thenReturn(
                new LocalizedContentResponse(Language.TA, Language.TA, true, false, "Tamil Title", "Tamil Summary"));

        Page<NotificationResponse> result = service.getLocalizedNotifications("user1", Language.TA, pageable);

        assertThat(result.getContent()).hasSize(1);
        NotificationResponse response = result.getContent().get(0);
        assertThat(response.title()).isEqualTo("Tamil Title");
        assertThat(response.message()).isEqualTo("Tamil Summary");
        assertThat(response.localizedContent().resolvedLanguage()).isEqualTo(Language.TA);
    }

    @Test
    void getLocalizedNotifications_fallsBackToUserPreferenceWhenLanguageNotExplicit() {
        Pageable pageable = PageRequest.of(0, 10);
        Notification notification = createNotification("n1", "user1", "art1", null, "Sinhala Title", "Sinhala Msg");
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("user1", pageable))
                .thenReturn(new PageImpl<>(List.of(notification)));
        when(userPreferencesService.get("user1")).thenReturn(
                new UserPreferencesResponse(DisplayLanguagePreference.EN, List.of(), true, now, now));

        Article article = createArticle("art1", Language.SI, "Sinhala Title");
        when(articleRepository.findAllById(Set.of("art1"))).thenReturn(List.of(article));
        when(articleLocalizationService.localize(article, Language.EN)).thenReturn(
                new LocalizedContentResponse(Language.EN, Language.EN, true, false, "English Title", "English Summary"));

        Page<NotificationResponse> result = service.getLocalizedNotifications("user1", null, pageable);

        assertThat(result.getContent()).hasSize(1);
        NotificationResponse response = result.getContent().get(0);
        assertThat(response.title()).isEqualTo("English Title");
        assertThat(response.message()).isEqualTo("English Summary");
    }

    private Notification createNotification(String id, String userId, String articleId, String storyId, String title, String msg) {
        return new Notification(
                id, userId, Notification.NotificationType.STORY_ACTIVITY, storyId, articleId,
                "src1", "source-slug", "Source Name", List.of(Notification.NotificationReason.FOLLOWED_TOPIC),
                title, msg, "/story/123", "v1", "dedupe-" + id, now, null, null, null
        );
    }

    private Article createArticle(String id, Language lang, String title) {
        return new Article(id, "src1", title, "https://example.com/" + id,
                "https://example.com/" + id, lang, List.of(), now, now,
                ArticleCategory.LOCAL, "BODY", "hash", null, null,
                Map.of(), ProcessingStatus.COMPLETED, "story1", now, now);
    }
}
