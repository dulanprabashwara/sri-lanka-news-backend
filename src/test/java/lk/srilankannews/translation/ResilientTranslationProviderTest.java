package lk.srilankannews.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import lk.srilankannews.common.domain.Language;
import org.junit.jupiter.api.Test;

class ResilientTranslationProviderTest {
    private final TranslationInput input = new TranslationInput(
            Language.EN, "Heavy rain", "Storms expected", Set.of(Language.SI));

    @Test
    void retriesTransientGeminiFailureThenUsesAzureFallback() {
        TranslationProvider primary = mock(TranslationProvider.class);
        TranslationProvider fallback = mock(TranslationProvider.class);
        when(primary.translate(input)).thenThrow(new TranslationProviderException(
                TranslationProviderException.Kind.RATE_LIMIT, "Gemini rate limited"));
        var expected = List.of(new TranslatedContent(
                Language.SI, "වැසි", "කුණාටු", "AZURE_TRANSLATOR", "text-translation-v3"));
        when(fallback.translate(input)).thenReturn(expected);
        var provider = new ResilientTranslationProvider(
                primary, fallback, new TranslationReliabilityProperties(3, Duration.ZERO),
                new TranslationOutputValidator(),
                ignored -> { });

        assertThat(provider.translate(input)).isEqualTo(expected);
        verify(primary, times(3)).translate(input);
        verify(fallback).translate(input);
    }

    @Test
    void permanentValidationFailureDoesNotCallFallback() {
        TranslationProvider primary = mock(TranslationProvider.class);
        TranslationProvider fallback = mock(TranslationProvider.class);
        when(primary.translate(input)).thenThrow(new TranslationProviderException(
                TranslationProviderException.Kind.VALIDATION, "Invalid input"));
        var provider = new ResilientTranslationProvider(
                primary, fallback, new TranslationReliabilityProperties(3, Duration.ZERO),
                new TranslationOutputValidator(),
                ignored -> { });

        assertThatThrownBy(() -> provider.translate(input))
                .isInstanceOf(TranslationProviderException.class)
                .hasMessage("Invalid input");
        verify(primary).translate(input);
        verify(fallback, never()).translate(input);
    }

    @Test
    void invalidGeminiOutputIsValidatedInsideRetryBoundaryThenUsesAzureFallback() {
        TranslationInput automaticInput = new TranslationInput(
                Language.SI, "Sinhala source title", "Sinhala source summary",
                Set.of(Language.EN, Language.TA));
        TranslationProvider primary = mock(TranslationProvider.class);
        TranslationProvider fallback = mock(TranslationProvider.class);
        when(primary.translate(automaticInput)).thenReturn(List.of(
                new TranslatedContent(
                        Language.EN, "English title", "English summary",
                        "GEMINI", "gemini-test"),
                new TranslatedContent(
                        Language.TA, automaticInput.title(), "Tamil summary",
                        "GEMINI", "gemini-test")));
        var expected = List.of(
                new TranslatedContent(
                        Language.EN, "English title", "English summary",
                        "AZURE_TRANSLATOR", "text-translation-v3"),
                new TranslatedContent(
                        Language.TA, "Tamil title", "Tamil summary",
                        "AZURE_TRANSLATOR", "text-translation-v3"));
        when(fallback.translate(automaticInput)).thenReturn(expected);
        var provider = new ResilientTranslationProvider(
                primary, fallback, new TranslationReliabilityProperties(3, Duration.ZERO),
                new TranslationOutputValidator(),
                ignored -> { });

        assertThat(provider.translate(automaticInput)).isEqualTo(expected);
        verify(primary, times(3)).translate(automaticInput);
        verify(fallback).translate(automaticInput);
    }
}
