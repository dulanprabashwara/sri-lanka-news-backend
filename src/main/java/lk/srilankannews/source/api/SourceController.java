package lk.srilankannews.source.api;

import jakarta.validation.constraints.Pattern;
import java.util.List;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.source.SourceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/sources")
public class SourceController {

    private final SourceService sourceService;
    private final SourceApiMapper sourceApiMapper;

    public SourceController(SourceService sourceService, SourceApiMapper sourceApiMapper) {
        this.sourceService = sourceService;
        this.sourceApiMapper = sourceApiMapper;
    }

    @GetMapping
    public List<SourceResponse> list() {
        return sourceService.findAllByName().stream()
                .map(sourceApiMapper::toResponse)
                .toList();
    }

    @GetMapping("/{slug}")
    public SourceResponse detail(
            @PathVariable
            @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "must be a lowercase URL-safe slug")
            String slug
    ) {
        return sourceService.findBySlug(slug)
                .map(sourceApiMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Source"));
    }
}
