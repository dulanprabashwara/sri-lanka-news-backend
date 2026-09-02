package lk.srilankannews.user;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleService;
import lk.srilankannews.article.api.ArticleApiMapper;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(ForYouProperties.class)
public class ForYouService {
    private static final Comparator<ForYouRanking.RankedArticle> ORDER = Comparator
            .comparingInt(ForYouRanking.RankedArticle::score).reversed()
            .thenComparing(item -> item.article().publishedAt(), Comparator.reverseOrder())
            .thenComparing(item -> item.article().id(), Comparator.reverseOrder());

    private final UserPreferencesService preferencesService;
    private final UserFollowRepository followRepository;
    private final ArticleService articleService;
    private final SourceService sourceService;
    private final ForYouRanking ranking;
    private final ArticleApiMapper articleMapper;
    private final ForYouProperties properties;

    public ForYouService(UserPreferencesService preferencesService,
            UserFollowRepository followRepository, ArticleService articleService,
            SourceService sourceService, ForYouRanking ranking, ArticleApiMapper articleMapper,
            ForYouProperties properties) {
        this.preferencesService = preferencesService;
        this.followRepository = followRepository;
        this.articleService = articleService;
        this.sourceService = sourceService;
        this.ranking = ranking;
        this.articleMapper = articleMapper;
        this.properties = properties;
    }

    public ForYouFeedResponse feed(
            String userId, int page, int size, Language explicitDisplayLanguage) {
        UserPreferencesResponse preferences = preferencesService.get(userId);
        List<UserFollow> follows = followRepository.findAllByUserId(userId);
        List<Article> candidates = articleService.findRecent(properties.candidateLimit());

        Set<String> followedSourceIds = follows.stream()
                .filter(follow -> follow.targetType() == FollowTargetType.SOURCE)
                .map(UserFollow::targetKey).collect(Collectors.toSet());
        Map<String, String> followedTopics = follows.stream()
                .filter(follow -> follow.targetType() == FollowTargetType.TOPIC)
                .collect(Collectors.toMap(UserFollow::targetKey, UserFollow::displayLabel,
                        (first, ignored) -> first));
        Set<ArticleCategory> categories = Set.copyOf(preferences.preferredCategories());

        Set<String> requiredSourceIds = candidates.stream().map(Article::sourceId)
                .collect(Collectors.toCollection(HashSet::new));
        requiredSourceIds.addAll(followedSourceIds);
        Map<String, Source> sources = sourceService.findAllByIds(requiredSourceIds).stream()
                .collect(Collectors.toMap(Source::id, Function.identity()));
        followedSourceIds.retainAll(sources.keySet());

        List<ForYouRanking.RankedArticle> ranked = new ArrayList<>();
        for (Article article : candidates) {
            Source source = sources.get(article.sourceId());
            if (source != null) {
                ranked.add(ranking.rank(
                        article, source, followedSourceIds, followedTopics, categories));
            }
        }
        ranked.sort(ORDER);

        Language displayLanguage = explicitDisplayLanguage != null
                ? explicitDisplayLanguage : language(preferences.preferredDisplayLanguage());
        int from = (int) Math.min((long) page * size, ranked.size());
        int to = Math.min(from + size, ranked.size());
        List<ForYouItemResponse> content = ranked.subList(from, to).stream()
                .map(item -> new ForYouItemResponse(
                        articleMapper.toResponse(item.article(), item.source(), displayLanguage),
                        item.score() > 0, item.reasons()))
                .toList();
        int totalPages = ranked.isEmpty() ? 0 : (ranked.size() + size - 1) / size;
        int signalCount = followedSourceIds.size() + followedTopics.size() + categories.size();
        return new ForYouFeedResponse(content, page, size, ranked.size(), totalPages,
                page == 0, page >= totalPages - 1,
                new PersonalizationResponse(signalCount > 0, signalCount));
    }

    private Language language(DisplayLanguagePreference preference) {
        return preference == DisplayLanguagePreference.ORIGINAL
                ? null : Language.valueOf(preference.name());
    }
}
