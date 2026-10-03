package io.nop.ai.core.reliability;

import io.nop.ai.api.chat.ErrorClassification;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Plan 2304 (WI22, dsh-D4 ⑥-建议3): regex classifier for provider
 * unstructured error texts — maps known message/errorType tokens onto the
 * canonical {@link ErrorClassification} values, complementing (not replacing)
 * the cause-chain unwrapping of {@link LlmErrorClassifier}.
 *
 * <p><b>Fail-open</b> (dsh error.ts disposition): only strong token matches
 * classify; an unknown text returns {@code null} and the caller keeps its own
 * heuristic — the classifier is never the sole reason a failure changes
 * channel. Matching uses {@code find} (substring) semantics, deliberately NOT
 * the {@code matches()} full-match convention of the config
 * {@code <errorMappings>} messagePattern — bare tokens like {@code overloaded}
 * must hit real-world texts such as "Rate limit exceeded: try again later".
 *
 * <p><b>Status gate (plan 2304 round 1 M6)</b>: the QUOTA/AUTH rows only fire
 * when {@code statusAllowsQuotaAuth} — a 5xx text like "insufficient
 * capacity" must not divert a transient server error into the account-chain
 * switch channel. Exception-text classification (no status) is treated as
 * satisfying the gate (round 2 N2).
 *
 * <p>Deliberately NO OVERFLOW row — the overflow recovery loop belongs to the
 * autonomous roadmap (WI4/WI5); context-length texts map to NON_TRANSIENT.
 */
public final class ErrorMessageClassifier {

    private ErrorMessageClassifier() {
    }

    private static final Map<Pattern, ErrorClassification> RETRYABLE_PATTERNS = new LinkedHashMap<>();

    private static final Map<Pattern, ErrorClassification> QUOTA_AUTH_PATTERNS = new LinkedHashMap<>();

    static {
        // retryable rows — safe on any status
        RETRYABLE_PATTERNS.put(Pattern.compile("server_is_overloaded|overloaded_error|slow_down|overloaded",
                Pattern.CASE_INSENSITIVE), ErrorClassification.OVERLOADED);
        RETRYABLE_PATTERNS.put(Pattern.compile("try again|rate[- ]limit|rate-limited|too many requests",
                Pattern.CASE_INSENSITIVE), ErrorClassification.RATE_LIMITED);
        RETRYABLE_PATTERNS.put(Pattern.compile("context length|maximum context|too many tokens|prompt is too long",
                Pattern.CASE_INSENSITIVE), ErrorClassification.NON_TRANSIENT);

        // account-chain rows — gated to <500 (5xx texts must not divert a
        // transient server error into the switch channel)
        QUOTA_AUTH_PATTERNS.put(Pattern.compile("quota|billing|insufficient",
                Pattern.CASE_INSENSITIVE), ErrorClassification.QUOTA_EXCEEDED);
        QUOTA_AUTH_PATTERNS.put(Pattern.compile("invalid api key|unauthorized|authentication",
                Pattern.CASE_INSENSITIVE), ErrorClassification.AUTH_INVALID);
    }

    /**
     * Classify a provider error text.
     *
     * @param combinedText         errorType + errorCode + errorMessage joined
     *                             (the only place errorType tokens like
     *                             {@code overloaded_error} can match)
     * @param statusAllowsQuotaAuth whether the HTTP status is below 500 (or
     *                             unknown — exception texts satisfy the gate)
     * @return the classification, or {@code null} when no known token matched
     *         (fail-open: caller keeps its own heuristic)
     */
    public static ErrorClassification classify(String combinedText, boolean statusAllowsQuotaAuth) {
        if (combinedText == null || combinedText.isEmpty()) {
            return null;
        }
        for (Map.Entry<Pattern, ErrorClassification> entry : RETRYABLE_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(combinedText).find()) {
                return entry.getValue();
            }
        }
        if (statusAllowsQuotaAuth) {
            for (Map.Entry<Pattern, ErrorClassification> entry : QUOTA_AUTH_PATTERNS.entrySet()) {
                if (entry.getKey().matcher(combinedText).find()) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }
}
