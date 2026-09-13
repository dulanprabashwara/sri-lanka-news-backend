package lk.srilankannews.notifications;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.api.ArticleLocalizationService;
import lk.srilankannews.article.api.LocalizedContentResponse;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryRepository;
import lk.srilankannews.user.DisplayLanguagePreference;
import lk.srilankannews.user.UserPreferencesResponse;
import lk.srilankannews.user.UserPreferencesService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class NotificationLocalizationService {

    private final NotificationRepository notificationRepository;
    private final ArticleRepository articleRepository;
    private final StoryRepository storyRepository;
    private final ArticleLocalizationService articleLocalizationService;
    private final UserPreferencesService userPreferencesService;

    public NotificationLocalizationService(
            NotificationRepository notificationRepository,
            ArticleRepository articleRepository,
            StoryRepository storyRepository,
            ArticleLocalizationService articleLocalizationService,
            UserPreferencesService userPreferencesService) {
        this.notificationRepository = notificationRepository;
        this.articleRepository = articleRepository;
        this.storyRepository = storyRepository;
        this.articleLocalizationService = articleLocalizationService;
        this.userPreferencesService = userPreferencesService;
    }

    public Page<NotificationResponse> getLocalizedNotifications(
            String userId, Language requestedLanguage, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        if (page.isEmpty()) {
            return page.map(NotificationResponse::from);
        }

        Language effectiveLanguage = requestedLanguage;
        if (effectiveLanguage == null && userPreferencesService != null) {
            UserPreferencesResponse prefs = userPreferencesService.get(userId);
            if (prefs != null && prefs.preferredDisplayLanguage() != null
                    && prefs.preferredDisplayLanguage() != DisplayLanguagePreference.ORIGINAL) {
                try {
                    effectiveLanguage = Language.valueOf(prefs.preferredDisplayLanguage().name());
                } catch (IllegalArgumentException ignored) {
                    effectiveLanguage = null;
                }
            }
        }

        if (effectiveLanguage == null) {
            return page.map(NotificationResponse::from);
        }

        Set<String> storyIdsNeedingLookup = page.getContent().stream()
                .filter(n -> n.triggeringArticleId() == null && n.storyId() != null)
                .map(Notification::storyId)
                .collect(Collectors.toSet());

        Map<String, String> storyToArticleId = storyIdsNeedingLookup.isEmpty()
                ? Map.of()
                : storyRepository.findAllById(storyIdsNeedingLookup).stream()
                        .filter(s -> s.representativeArticleId() != null)
                        .collect(Collectors.toMap(Story::id, Story::representativeArticleId, (a, b) -> a));

        Set<String> articleIds = new HashSet<>();
        for (Notification n : page.getContent()) {
            if (n.triggeringArticleId() != null) {
                articleIds.add(n.triggeringArticleId());
            } else if (n.storyId() != null && storyToArticleId.containsKey(n.storyId())) {
                articleIds.add(storyToArticleId.get(n.storyId()));
            }
        }

        Map<String, Article> articleMap = articleIds.isEmpty()
                ? Map.of()
                : articleRepository.findAllById(articleIds).stream()
                        .collect(Collectors.toMap(Article::id, Function.identity(), (a, b) -> a));

        final Language targetLang = effectiveLanguage;
        return page.map(notification -> {
            String articleId = notification.triggeringArticleId();
            if (articleId == null && notification.storyId() != null) {
                articleId = storyToArticleId.get(notification.storyId());
            }
            Article article = articleId != null ? articleMap.get(articleId) : null;
            if (article == null) {
                return NotificationResponse.from(notification);
            }

            LocalizedContentResponse localized = articleLocalizationService.localize(article, targetLang);
            if (localized == null) {
                return NotificationResponse.from(notification);
            }

            String title = (localized.title() != null && !localized.title().isBlank())
                    ? localized.title() : notification.title();
            String message = (localized.summary() != null && !localized.summary().isBlank())
                    ? localized.summary() : notification.message();

            return new NotificationResponse(
                    notification.id(),
                    notification.userId(),
                    notification.type(),
                    notification.storyId(),
                    notification.triggeringArticleId(),
                    notification.sourceId(),
                    notification.sourceSlug(),
                    notification.sourceName(),
                    notification.reasons(),
                    title,
                    message,
                    notification.linkPath(),
                    notification.eventVersion(),
                    notification.dedupeKey(),
                    notification.createdAt(),
                    notification.readAt(),
                    notification.emailDelivery(),
                    localized
            );
        });
    }
}

