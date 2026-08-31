package lk.srilankannews.story;

import java.time.Clock;
import java.util.List;
import lk.srilankannews.ai.EmbeddingProvider;
import lk.srilankannews.article.Article;
import lk.srilankannews.article.ArticleSemanticEmbedding;
import lk.srilankannews.article.ArticleService;
import org.springframework.stereotype.Service;

@Service
public class ArticleEmbeddingService {
    private final ArticleService articleService;
    private final EmbeddingProvider provider;
    private final SemanticEmbeddingInputFactory inputFactory;
    private final StoryEmbeddingProperties properties;
    private final Clock clock;

    public ArticleEmbeddingService(
            ArticleService articleService,
            EmbeddingProvider provider,
            SemanticEmbeddingInputFactory inputFactory,
            StoryEmbeddingProperties properties,
            Clock clock) {
        this.articleService = articleService;
        this.provider = provider;
        this.inputFactory = inputFactory;
        this.properties = properties;
        this.clock = clock;
    }

    public Article ensureEmbedding(String articleId) {
        Article article = articleService.findById(articleId)
                .orElseThrow(() -> new IllegalStateException("Article does not exist for embedding"));
        SemanticEmbeddingInputFactory.Input input = inputFactory.create(article);
        ArticleSemanticEmbedding existing = article.semanticEmbedding();
        if (existing != null && existing.matches(
                properties.model(),
                properties.dimensions(),
                input.version(),
                input.hash())) {
            return article;
        }

        List<Double> values = List.copyOf(provider.embed(input.text()));
        validate(values);
        ArticleSemanticEmbedding embedding = new ArticleSemanticEmbedding(
                values,
                properties.model(),
                properties.dimensions(),
                input.version(),
                input.hash(),
                clock.instant());
        return articleService.saveSemanticEmbedding(articleId, embedding)
                .orElseThrow(() -> new IllegalStateException(
                        "Article disappeared during embedding persistence"));
    }

    private void validate(List<Double> values) {
        if (values.size() != properties.dimensions()) {
            throw new IllegalStateException("Embedding dimensions do not match configuration");
        }
        if (values.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalStateException("Embedding contains invalid values");
        }
    }
}
