package lk.srilankannews.story.ask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lk.srilankannews.ai.EmbeddingProvider;
import lk.srilankannews.ai.SemanticSimilarityEmbeddingInput;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.ArticleSemanticEmbedding;
import lk.srilankannews.article.api.ArticleLocalizationService;
import lk.srilankannews.article.api.LocalizedContentResponse;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.StoryEmbeddingProperties;
import lk.srilankannews.story.StoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import lk.srilankannews.processing.enrichment.GeminiRequestController;

@Service
public class AskStoryService {
    private static final double SOURCE_DIVERSITY_SCORE_TOLERANCE = 0.02;
    private static final Pattern SOURCE_MARKER = Pattern.compile("\\[(S\\d+)]");
    private static final Pattern HTML = Pattern.compile("<[^>]+>");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");

    private final StoryRepository storyRepository;
    private final ArticleRepository articleRepository;
    private final SourceService sourceService;
    private final EmbeddingProvider embeddingProvider;
    private final GroundedAnswerProvider answerProvider;
    private final ArticleLocalizationService localizationService;
    private final AskStoryQuestionNormalizer normalizer;
    private final StoryGroundingContextBuilder contextBuilder;
    private final StoryEmbeddingProperties embeddingProperties;
    private final AskStoryProperties askProperties;
    private final lk.srilankannews.analytics.AnalyticsRecorder analyticsRecorder;
    private final GeminiRequestController geminiRequestController;

    @org.springframework.beans.factory.annotation.Autowired
    public AskStoryService(
            StoryRepository storyRepository, ArticleRepository articleRepository,
            SourceService sourceService, EmbeddingProvider embeddingProvider,
            @org.springframework.beans.factory.annotation.Qualifier("groundedAnswerProvider") GroundedAnswerProvider answerProvider,
            ArticleLocalizationService localizationService,
            AskStoryQuestionNormalizer normalizer,
            StoryGroundingContextBuilder contextBuilder,
            StoryEmbeddingProperties embeddingProperties,
            AskStoryProperties askProperties,
            lk.srilankannews.analytics.AnalyticsRecorder analyticsRecorder,
            GeminiRequestController geminiRequestController) {
        this.storyRepository = storyRepository;
        this.articleRepository = articleRepository;
        this.sourceService = sourceService;
        this.embeddingProvider = embeddingProvider;
        this.answerProvider = answerProvider;
        this.localizationService = localizationService;
        this.normalizer = normalizer;
        this.contextBuilder = contextBuilder;
        this.embeddingProperties = embeddingProperties;
        this.askProperties = askProperties;
        this.analyticsRecorder = analyticsRecorder;
        this.geminiRequestController = geminiRequestController;
    }

    public AskStoryService(
            StoryRepository storyRepository, ArticleRepository articleRepository,
            SourceService sourceService, EmbeddingProvider embeddingProvider,
            GroundedAnswerProvider answerProvider,
            ArticleLocalizationService localizationService,
            AskStoryQuestionNormalizer normalizer,
            StoryGroundingContextBuilder contextBuilder,
            StoryEmbeddingProperties embeddingProperties,
            AskStoryProperties askProperties,
            lk.srilankannews.analytics.AnalyticsRecorder analyticsRecorder) {
        this(storyRepository, articleRepository, sourceService, embeddingProvider,
                answerProvider, localizationService, normalizer, contextBuilder,
                embeddingProperties, askProperties, analyticsRecorder, null);
    }

    public AskStoryResponse ask(String storyId, AskStoryRequest request) {
        String question = normalizer.normalize(request.question());
        Language displayLanguage = request.displayLanguage() == null ? Language.EN : request.displayLanguage();
        Story story = storyRepository.findById(storyId)
                .filter(Story::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Story"));
        List<Article> members = articleRepository.findByStoryId(
                storyId, Sort.by(Sort.Order.asc("publishedAt"), Sort.Order.asc("id")));
        List<Article> usable = members.stream().filter(this::hasUsableEvidence).toList();
        if (usable.isEmpty()) {
            recordAnalytics(storyId, false);
            return insufficient(storyId, displayLanguage);
        }
        Map<String, Source> sources = sources(usable);

        List<Double> queryVector;
        try {
            if (geminiRequestController != null && !answerProvider.hasFallback()) {
                geminiRequestController.requireInteractiveAvailability();
            }
            queryVector = List.copyOf(embeddingProvider.embed(
                    SemanticSimilarityEmbeddingInput.format(question)));
            validateQueryVector(queryVector);
        } catch (RuntimeException exception) {
            recordAnalytics(storyId, false);
            throw new AskStoryUnavailableException(exception);
        }

        List<Article> selected = select(usable, queryVector);
        StoryGroundingContextBuilder.BuiltContext context;
        try {
            context = contextBuilder.build(story, usable, selected, sources);
        } catch (RuntimeException exception) {
            recordAnalytics(storyId, false);
            throw new AskStoryUnavailableException(exception);
        }
        if (context.sources().isEmpty()) {
            recordAnalytics(storyId, false);
            return insufficient(storyId, displayLanguage);
        }
        selected = List.copyOf(selected.subList(0, context.sources().size()));

        GroundedAnswerResult result;
        try {
            result = answerProvider.answer(new GroundedAnswerInput(
                    question, displayLanguage, story.canonicalTitle(), story.category(),
                    story.firstPublishedAt(), story.lastPublishedAt(), context.inventory(),
                    context.sources()));
        } catch (RuntimeException exception) {
            recordAnalytics(storyId, false);
            throw new AskStoryUnavailableException(exception);
        }
        AskStoryResponse response = validateAndMap(storyId, result, selected, sources, displayLanguage);
        recordAnalytics(storyId, response.answerable());
        return response;
    }

    private void recordAnalytics(String storyId, boolean success) {
        analyticsRecorder.recordBestEffort(
                lk.srilankannews.analytics.AnalyticsEventType.ASK_STORY_COMPLETED,
                java.util.UUID.randomUUID().toString(),
                null,
                storyId,
                null,
                success ? 1 : 0,
                null,
                null
        );
    }

    private List<Article> select(List<Article> articles, List<Double> queryVector) {
        List<RankedArticle> ranked = articles.stream()
                .map(article -> new RankedArticle(article, similarity(article, queryVector)))
                .sorted(Comparator.comparingDouble(RankedArticle::score).reversed()
                        .thenComparing(item -> item.article().publishedAt(), Comparator.reverseOrder())
                        .thenComparing(item -> item.article().id()))
                .toList();
        if (ranked.size() <= askProperties.maxRetrievedArticles()) {
            return ranked.stream().map(RankedArticle::article).toList();
        }

        List<RankedArticle> remaining = new ArrayList<>(ranked);
        List<RankedArticle> chosen = new ArrayList<>();
        Set<String> usedSources = new LinkedHashSet<>();
        while (!remaining.isEmpty() && chosen.size() < askProperties.maxRetrievedArticles()) {
            RankedArticle best = remaining.get(0);
            RankedArticle selected = best;
            if (usedSources.contains(best.article().sourceId())) {
                selected = remaining.stream()
                        .filter(candidate -> !usedSources.contains(candidate.article().sourceId()))
                        .filter(candidate -> candidate.score()
                                >= best.score() - SOURCE_DIVERSITY_SCORE_TOLERANCE)
                        .findFirst()
                        .orElse(best);
            }
            chosen.add(selected);
            usedSources.add(selected.article().sourceId());
            remaining.remove(selected);
        }
        chosen.sort(Comparator.comparingDouble(RankedArticle::score).reversed()
                .thenComparing(item -> item.article().publishedAt(), Comparator.reverseOrder())
                .thenComparing(item -> item.article().id()));
        return chosen.stream().map(RankedArticle::article).toList();
    }

    private double similarity(Article article, List<Double> queryVector) {
        ArticleSemanticEmbedding embedding = article.semanticEmbedding();
        if (!compatible(embedding)) {
            return -2.0;
        }
        double dot = 0;
        double leftMagnitude = 0;
        double rightMagnitude = 0;
        for (int index = 0; index < queryVector.size(); index++) {
            double left = queryVector.get(index);
            double right = embedding.values().get(index);
            if (!Double.isFinite(right)) {
                return -2.0;
            }
            dot += left * right;
            leftMagnitude += left * left;
            rightMagnitude += right * right;
        }
        return leftMagnitude == 0 || rightMagnitude == 0
                ? -1.0
                : dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private boolean compatible(ArticleSemanticEmbedding embedding) {
        return embedding != null
                && embeddingProperties.model().equals(embedding.model())
                && embeddingProperties.dimensions() == embedding.dimensions()
                && embeddingProperties.inputVersion().equals(embedding.inputVersion())
                && embedding.values().size() == embeddingProperties.dimensions();
    }

    private void validateQueryVector(List<Double> vector) {
        if (vector.size() != embeddingProperties.dimensions()
                || vector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalStateException("Query embedding is incompatible.");
        }
    }

    private AskStoryResponse validateAndMap(
            String storyId, GroundedAnswerResult result, List<Article> selected,
            Map<String, Source> sources, Language displayLanguage) {
        if (result.answer() == null || result.answer().isBlank()
                || result.answer().length() > askProperties.maxAnswerCharacters()
                || HTML.matcher(result.answer()).find()
                || URL.matcher(result.answer()).find()) {
            throw new AskStoryUnavailableException("Grounded answer output is invalid.");
        }
        if (!result.answerable()) {
            if (!result.citedSourceIds().isEmpty() || SOURCE_MARKER.matcher(result.answer()).find()) {
                throw new AskStoryUnavailableException("Grounded answer citations are invalid.");
            }
            return new AskStoryResponse(storyId, false, result.answer().trim(), List.of());
        }

        Map<String, Article> articlesByLabel = new LinkedHashMap<>();
        for (int index = 0; index < selected.size(); index++) {
            articlesByLabel.put("S" + (index + 1), selected.get(index));
        }
        LinkedHashSet<String> declared = new LinkedHashSet<>(result.citedSourceIds());
        if (declared.isEmpty() || declared.stream().anyMatch(id -> !articlesByLabel.containsKey(id))) {
            throw new AskStoryUnavailableException("Grounded answer citations are invalid.");
        }
        LinkedHashSet<String> markers = new LinkedHashSet<>();
        Matcher matcher = SOURCE_MARKER.matcher(result.answer());
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!articlesByLabel.containsKey(id)) {
                throw new AskStoryUnavailableException("Grounded answer citations are invalid.");
            }
            markers.add(id);
        }
        if (markers.isEmpty() || !markers.equals(declared)) {
            throw new AskStoryUnavailableException("Grounded answer citations are invalid.");
        }

        Map<String, Integer> numbers = new LinkedHashMap<>();
        int number = 1;
        for (String marker : markers) {
            numbers.put(marker, number++);
        }
        String publicAnswer = result.answer();
        for (Map.Entry<String, Integer> entry : numbers.entrySet()) {
            publicAnswer = publicAnswer.replace("[" + entry.getKey() + "]", "[" + entry.getValue() + "]");
        }
        List<AskStoryCitationResponse> citations = numbers.entrySet().stream()
                .map(entry -> citation(entry.getValue(), articlesByLabel.get(entry.getKey()),
                        sources, displayLanguage))
                .toList();
        return new AskStoryResponse(storyId, true, publicAnswer.trim(), citations);
    }

    private AskStoryCitationResponse citation(
            int number, Article article, Map<String, Source> sources, Language language) {
        Source source = sources.get(article.sourceId());
        LocalizedContentResponse localized = localizationService.localize(article, language);
        return new AskStoryCitationResponse(
                number, article.id(), localized == null ? article.title() : localized.title(),
                new AskStorySourceResponse(source.name(), source.slug()), article.publishedAt(),
                article.originalUrl());
    }

    private Map<String, Source> sources(List<Article> articles) {
        Set<String> ids = articles.stream().map(Article::sourceId).collect(Collectors.toSet());
        Map<String, Source> result = sourceService.findAllByIds(ids).stream()
                .collect(Collectors.toMap(Source::id, Function.identity()));
        if (!result.keySet().containsAll(ids)) {
            throw new AskStoryUnavailableException("Story source attribution is unavailable.");
        }
        return result;
    }

    private boolean hasUsableEvidence(Article article) {
        return notBlank(article.title()) || notBlank(article.extractedContent())
                || article.aiEnrichment() != null && notBlank(article.aiEnrichment().summary());
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private AskStoryResponse insufficient(String storyId, Language language) {
        String message = switch (language) {
            case SI -> "ලබා ගත හැකි වාර්තාවල මෙම ප්‍රශ්නයට පිළිතුරු දීමට ප්‍රමාණවත් තොරතුරු නොමැත.";
            case TA -> "கிடைக்கக்கூடிய செய்திகளில் இந்தக் கேள்விக்கு பதிலளிக்க போதுமான தகவல் இல்லை.";
            case EN -> "The available reports do not provide enough information to answer that question.";
        };
        return new AskStoryResponse(storyId, false, message, List.of());
    }

    private record RankedArticle(Article article, double score) {
    }
}
