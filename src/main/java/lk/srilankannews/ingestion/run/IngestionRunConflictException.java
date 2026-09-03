package lk.srilankannews.ingestion.run;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class IngestionRunConflictException extends RuntimeException {
    public IngestionRunConflictException(String message) {
        super(message);
    }
}
