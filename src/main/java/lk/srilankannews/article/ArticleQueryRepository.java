package lk.srilankannews.article;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ArticleQueryRepository {

    Page<Article> findAll(ArticleFilter filter, Pageable pageable);
}
