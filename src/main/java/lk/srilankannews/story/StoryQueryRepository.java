package lk.srilankannews.story;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StoryQueryRepository {

    Page<Story> findAll(StoryFilter filter, Pageable pageable);
}
