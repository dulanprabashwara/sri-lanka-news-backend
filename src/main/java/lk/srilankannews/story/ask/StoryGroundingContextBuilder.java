package lk.srilankannews.story.ask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lk.srilankannews.article.Article;
import lk.srilankannews.source.Source;
import lk.srilankannews.story.Story;
import org.springframework.stereotype.Component;

@Component
public class StoryGroundingContextBuilder {
    private static final int MAX_INVENTORY_ARTICLES = 50;
    private static final int MAX_INVENTORY_SUMMARY_CHARACTERS = 500;

    private final AskStoryProperties properties;

    public StoryGroundingContextBuilder(AskStoryProperties properties) {
        this.properties = properties;
    }

    public BuiltContext build(
            Story story, List<Article> inventoryArticles, List<Article> selectedArticles,
            Map<String, Source> sources) {
        StringBuilder inventory = new StringBuilder();
        int remaining = properties.maxTotalContextCharacters();
        for (Article article : inventoryArticles.stream().limit(MAX_INVENTORY_ARTICLES).toList()) {
            Source source = requireSource(article, sources);
            String line = "Article " + article.id() + " | Publisher: " + source.name()
                    + " | Published: " + article.publishedAt() + " | Category: " + article.category()
                    + " | Title: " + article.title() + " | Summary: "
                    + truncate(summary(article), MAX_INVENTORY_SUMMARY_CHARACTERS) + "\n";
            if (line.length() > remaining) {
                break;
            }
            inventory.append(line);
            remaining -= line.length();
        }

        List<GroundedSourceContext> contexts = new ArrayList<>();
        for (int index = 0; index < selectedArticles.size() && remaining > 0; index++) {
            Article article = selectedArticles.get(index);
            Source source = requireSource(article, sources);
            String title = truncate(article.title(), Math.min(1000, remaining));
            remaining -= title.length();
            String summary = truncate(summary(article), Math.min(2000, Math.max(0, remaining)));
            remaining -= summary.length();
            int contentLimit = Math.min(properties.maxArticleContextCharacters(), Math.max(0, remaining));
            String content = truncate(article.extractedContent(), contentLimit);
            remaining -= content.length();
            contexts.add(new GroundedSourceContext(
                    "S" + (index + 1), source.name(), title, article.publishedAt(),
                    article.category(), summary, content));
        }
        return new BuiltContext(inventory.toString().trim(), contexts);
    }

    private Source requireSource(Article article, Map<String, Source> sources) {
        Source source = sources.get(article.sourceId());
        if (source == null) {
            throw new IllegalStateException("Article source attribution is missing.");
        }
        return source;
    }

    private String summary(Article article) {
        return article.aiEnrichment() == null ? null : article.aiEnrichment().summary();
    }

    static String truncate(String value, int maximum) {
        if (value == null || value.isBlank() || maximum <= 0) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maximum) {
            return trimmed;
        }
        if (maximum == 1) {
            return "…";
        }
        int end = maximum - 1;
        if (end > 0 && Character.isHighSurrogate(trimmed.charAt(end - 1))) {
            end--;
        }
        return trimmed.substring(0, end).stripTrailing() + "…";
    }

    public record BuiltContext(String inventory, List<GroundedSourceContext> sources) {
        public BuiltContext {
            sources = List.copyOf(sources);
        }
    }
}
