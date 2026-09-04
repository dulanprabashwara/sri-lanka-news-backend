package lk.srilankannews.admin;

import lk.srilankannews.article.ProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.srilankannews.article.Article;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceRepository;
import lk.srilankannews.common.api.error.ResourceNotFoundException;

@RestController
@RequestMapping("/api/v1/admin/processing")
@PreAuthorize("@adminAuthorization.isAdmin(authentication)")
public class AdminProcessingController {

    private final AdminMongoOperations operations;
    private final SourceRepository sourceRepository;

    public AdminProcessingController(AdminMongoOperations operations, SourceRepository sourceRepository) {
        this.operations = operations;
        this.sourceRepository = sourceRepository;
    }

    @GetMapping
    public Page<AdminArticleResponse> processingArticles(
            @RequestParam(required = false) ProcessingStatus status,
            @RequestParam(required = false) String sourceSlug,
            @PageableDefault(sort = "discoveredAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
            
        String sourceId = sourceSlug == null ? null : sourceRepository.findBySlug(sourceSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Source")).id();
                
        Page<Article> articles = operations.findArticlesPage(status, sourceId, pageable);
        
        Set<String> sourceIds = articles.stream().map(Article::sourceId).collect(Collectors.toSet());
        Map<String, Source> sources = sourceIds.isEmpty() ? Map.of()
                : java.util.stream.StreamSupport.stream(sourceRepository.findAllById(sourceIds).spliterator(), false)
                        .collect(Collectors.toUnmodifiableMap(Source::id, Function.identity()));
                        
        return articles.map(article -> {
            Source source = sources.get(article.sourceId());
            if (source == null) throw new IllegalStateException("Article source is missing.");
            return new AdminArticleResponse(
                    article.id(), article.title(), new AdminSourceSummary(source.name(), source.slug()),
                    article.processingStatus(), article.discoveredAt(), article.publishedAt());
        });
    }
}
