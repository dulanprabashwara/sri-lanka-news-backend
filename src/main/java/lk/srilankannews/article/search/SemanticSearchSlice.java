package lk.srilankannews.article.search;

import java.util.List;
import lk.srilankannews.article.Article;

record SemanticSearchSlice(List<Article> content, boolean hasMore) {
    SemanticSearchSlice {
        content = List.copyOf(content);
    }
}
