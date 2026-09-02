package lk.srilankannews.story;

import java.time.Instant;
import java.util.List;
import lk.srilankannews.article.ArticleCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StoryQueryRepository {

    Page<Story> findAll(StoryFilter filter, Pageable pageable);

    List<Story> findTrendingCandidates(
            Instant publishedSince, ArticleCategory category, int limit);
}
