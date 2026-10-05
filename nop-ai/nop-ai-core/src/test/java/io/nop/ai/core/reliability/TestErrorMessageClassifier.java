package io.nop.ai.core.reliability;

import io.nop.ai.api.chat.ErrorClassification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plan 2304 (WI22): the ErrorMessageClassifier — fail-open token matching
 * (find semantics, not matches()), the QUOTA/AUTH <500 gate, and the wiring
 * into {@link LlmErrorClassifier} (message classification as the last resort
 * after the cause-chain walk — dsh-D4 ⑥-建议3).
 */
public class TestErrorMessageClassifier {

    @Test
    void overloadTokensClassifyRegardlessOfStatusGate() {
        assertEquals(ErrorClassification.OVERLOADED,
                ErrorMessageClassifier.classify("overloaded_error", false));
        assertEquals(ErrorClassification.OVERLOADED,
                ErrorMessageClassifier.classify("Upstream: server_is_overloaded", true));
        assertEquals(ErrorClassification.OVERLOADED,
                ErrorMessageClassifier.classify("please slow_down", true));
        // bare token, find semantics — real-world text shape
        assertEquals(ErrorClassification.OVERLOADED,
                ErrorMessageClassifier.classify("The server is Overloaded. Try later.", true));
    }

    @Test
    void rateLimitTokensClassify() {
        assertEquals(ErrorClassification.RATE_LIMITED,
                ErrorMessageClassifier.classify("Rate limit exceeded: try again later", true));
        assertEquals(ErrorClassification.RATE_LIMITED,
                ErrorMessageClassifier.classify("too many requests", false));
    }

    @Test
    void quotaAuthRowsAreGatedBelow500() {
        assertEquals(ErrorClassification.QUOTA_EXCEEDED,
                ErrorMessageClassifier.classify("insufficient quota", true));
        assertEquals(ErrorClassification.AUTH_INVALID,
                ErrorMessageClassifier.classify("invalid api key", true));
        // gate closed → the rows do not fire
        assertNull(ErrorMessageClassifier.classify("insufficient capacity", false),
                "5xx QUOTA-row text must not divert into the account-chain channel");
        assertNull(ErrorMessageClassifier.classify("authentication service down", false));
    }

    @Test
    void unknownTextIsFailOpen() {
        assertNull(ErrorMessageClassifier.classify("something entirely unexpected", true));
        assertNull(ErrorMessageClassifier.classify(null, true));
        assertNull(ErrorMessageClassifier.classify("", true));
    }

    @Test
    void contextLengthMapsToNonTransient() {
        assertEquals(ErrorClassification.NON_TRANSIENT,
                ErrorMessageClassifier.classify("prompt is too long: 200000 tokens > 128000 context limit", true));
    }

    @Test
    void llmErrorClassifierFallsThroughToMessageClassification() {
        // a plain exception carrying no timeout/status signal — the cause
        // chain walk ends without a classification, the message classifier
        // (dsh-D4 ⑥-建议3 wiring) classifies from the text
        assertEquals(ErrorClassification.RATE_LIMITED,
                LlmErrorClassifier.classify(new IllegalStateException("rate limit exceeded, slow down")));
        assertEquals(ErrorClassification.QUOTA_EXCEEDED,
                LlmErrorClassifier.classify(new IllegalStateException("You exceeded your billing quota")));
        // fail-open: unknown message keeps the existing TRANSIENT fallback
        assertEquals(ErrorClassification.TRANSIENT,
                LlmErrorClassifier.classify(new IllegalStateException("something entirely unexpected")));
    }
}
