package lk.srilankannews.source;

import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class SourceService {

    private final SourceRepository sourceRepository;
    private final Clock clock;

    public SourceService(SourceRepository sourceRepository, Clock clock) {
        this.sourceRepository = sourceRepository;
        this.clock = clock;
    }

    public Source create(@Valid CreateSourceCommand command) {
        if (sourceRepository.existsBySlug(command.slug())) {
            throw new DuplicateSourceSlugException(command.slug());
        }

        Instant now = clock.instant();
        try {
            return sourceRepository.save(Source.create(command, now));
        } catch (DuplicateKeyException exception) {
            throw new DuplicateSourceSlugException(command.slug());
        }
    }

    public Optional<Source> findById(String id) {
        return sourceRepository.findById(id);
    }

    public Optional<Source> findBySlug(String slug) {
        return sourceRepository.findBySlug(slug);
    }

    public List<Source> findAllByName() {
        return sourceRepository.findAllByOrderByNameAsc();
    }

    public List<Source> findAllByIds(Collection<String> ids) {
        return sourceRepository.findAllById(ids);
    }

    public boolean existsById(String id) {
        return sourceRepository.existsById(id);
    }
}
