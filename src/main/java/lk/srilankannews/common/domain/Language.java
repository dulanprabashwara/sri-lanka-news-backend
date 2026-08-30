package lk.srilankannews.common.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum Language {
    EN("en"),
    SI("si"),
    TA("ta");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static Language fromCode(String code) {
        return Arrays.stream(values())
                .filter(language -> language.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported language."));
    }
}
