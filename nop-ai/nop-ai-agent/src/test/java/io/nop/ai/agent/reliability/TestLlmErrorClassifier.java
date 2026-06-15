package io.nop.ai.agent.reliability;

import io.nop.ai.core.AiCoreErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopTimeoutException;
import org.junit.jupiter.api.Test;

import static io.nop.ai.core.AiCoreErrors.ARG_HTTP_STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plan 207 (L3-2) Phase 1 focused test for {@link LlmErrorClassifier}
 * (Minimum Rules #25). Verifies the error classification that drives the
 * {@link IRetryPolicy} decision: 429 → RATE_LIMITED, 5xx/timeout →
 * TRANSIENT, 4xx → NON_TRANSIENT, unknown network failures → TRANSIENT.
 */
public class TestLlmErrorClassifier {

    @Test
    void nullErrorClassifiesAsNonTransient() {
        assertEquals(ErrorClassification.NON_TRANSIENT, LlmErrorClassifier.classify(null));
    }

    @Test
    void timeoutExceptionClassifiesAsTransient() {
        assertEquals(ErrorClassification.TRANSIENT,
                LlmErrorClassifier.classify(new NopTimeoutException()));
    }

    @Test
    void http429ClassifiesAsRateLimited() {
        NopException ex = httpError(429);
        assertEquals(ErrorClassification.RATE_LIMITED, LlmErrorClassifier.classify(ex));
    }

    @Test
    void http500ClassifiesAsTransient() {
        assertEquals(ErrorClassification.TRANSIENT,
                LlmErrorClassifier.classify(httpError(500)));
    }

    @Test
    void http503ClassifiesAsTransient() {
        assertEquals(ErrorClassification.TRANSIENT,
                LlmErrorClassifier.classify(httpError(503)));
    }

    @Test
    void http400ClassifiesAsNonTransient() {
        assertEquals(ErrorClassification.NON_TRANSIENT,
                LlmErrorClassifier.classify(httpError(400)));
    }

    @Test
    void http401ClassifiesAsNonTransient() {
        assertEquals(ErrorClassification.NON_TRANSIENT,
                LlmErrorClassifier.classify(httpError(401)));
    }

    @Test
    void http403ClassifiesAsNonTransient() {
        assertEquals(ErrorClassification.NON_TRANSIENT,
                LlmErrorClassifier.classify(httpError(403)));
    }

    @Test
    void http404ClassifiesAsNonTransient() {
        assertEquals(ErrorClassification.NON_TRANSIENT,
                LlmErrorClassifier.classify(httpError(404)));
    }

    @Test
    void nopExceptionWithoutHttpStatusClassifiesAsTransient() {
        // A NopException without ARG_HTTP_STATUS is an unknown provider
        // failure → assume transient (safer default for network calls).
        NopException ex = new NopException(AiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR);
        assertEquals(ErrorClassification.TRANSIENT, LlmErrorClassifier.classify(ex));
    }

    @Test
    void genericRuntimeExceptionClassifiesAsTransient() {
        // Unknown runtime/network fault → assume transient.
        assertEquals(ErrorClassification.TRANSIENT,
                LlmErrorClassifier.classify(new RuntimeException("connection reset")));
    }

    @Test
    void timeoutInCauseChainClassifiesAsTransient() {
        // The http client may wrap a SocketTimeoutException inside a
        // NopException/CompletionException — the classifier walks the cause
        // chain to detect timeout markers.
        NopException wrapped = new NopException(AiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR,
                new java.net.SocketTimeoutException("read timed out"));
        assertEquals(ErrorClassification.TRANSIENT, LlmErrorClassifier.classify(wrapped));
    }

    @Test
    void http5xxWrappedInNopExceptionClassifiesAsTransient() {
        // The actual shape thrown by ChatServiceImpl: a NopException with
        // ERR_AI_SERVICE_HTTP_ERROR + ARG_HTTP_STATUS param.
        NopException ex = new NopException(AiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR)
                .param(ARG_HTTP_STATUS, 502);
        assertEquals(ErrorClassification.TRANSIENT, LlmErrorClassifier.classify(ex));
    }

    /**
     * Build a {@link NopException} carrying the ARG_HTTP_STATUS param, mirroring
     * the shape thrown by {@code ChatServiceImpl} on a non-200 response.
     */
    private static NopException httpError(int status) {
        return new NopException(AiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR)
                .param(ARG_HTTP_STATUS, status);
    }
}
