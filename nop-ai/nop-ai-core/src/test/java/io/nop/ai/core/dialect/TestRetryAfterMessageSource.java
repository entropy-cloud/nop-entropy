package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.core.model.LlmModel;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plan 2304 (WI22): the fourth Retry-After source — the error message text
 * ("try again in N s/ms/minutes", the unreal retry.go shape) — lowest
 * priority after the header/body sources, parse failures stay null. Also the
 * OVERLOADED classification end-to-end through parseErrorResponse (status
 * heuristic + message classifier).
 */
public class TestRetryAfterMessageSource extends JunitBaseTestCase {

    private final AbstractLlmDialect dialect = new OpenAiDialect();

    private static LlmModel plainConfig() {
        // the message-text source and the classifier depend on
        // errorResponse.errorMessagePath being configured (plan 2304 Known
        // Residual: unconfigured providers leave the fourth source inert)
        LlmModel config = new LlmModel();
        io.nop.ai.core.model.LlmErrorResponseModel errorResponse = new io.nop.ai.core.model.LlmErrorResponseModel();
        errorResponse.setErrorMessagePath("error.message");
        config.setErrorResponse(errorResponse);
        return config;
    }

    private ChatResponse parse(int status, String message) {
        String body = "{\"error\":{\"message\":\"" + (message == null ? "" : message) + "\"}}";
        return dialect.parseErrorResponse(body, status, null, plainConfig());
    }

    @Test
    void messageTextHintIsParsedAsSeconds() {
        ChatResponse r = parse(503, "Model is busy, please try again in 30s");
        assertEquals(Long.valueOf(30_000L), r.getRetryAfterMs(),
                "fourth source: 'try again in 30s' → 30000ms");
    }

    @Test
    void messageTextHintSupportsFractionalAndMinutesAndMs() {
        assertEquals(Long.valueOf(1_500L), parse(503, "try again in 1.5 seconds").getRetryAfterMs());
        assertEquals(Long.valueOf(120_000L), parse(503, "try again in 2 minutes").getRetryAfterMs());
        assertEquals(Long.valueOf(500L), parse(503, "try again in 500 ms").getRetryAfterMs());
        assertEquals(Long.valueOf(90_000L), parse(503, "try again in 1.5m").getRetryAfterMs());
    }

    @Test
    void headerSourceStillWinsOverMessageHint() {
        ChatResponse r = dialect.parseErrorResponse(
                "{\"error\":{\"message\":\"try again in 30s\"}}", 503,
                java.util.Map.of("retry-after", "5"), plainConfig());
        assertEquals(Long.valueOf(5_000L), r.getRetryAfterMs(),
                "the Retry-After header keeps priority over the message hint");
    }

    @Test
    void noHintAndUnparseableStayNull() {
        assertNull(parse(503, "internal server error").getRetryAfterMs(), "no hint → null");
        assertNull(parse(503, "try again in soon").getRetryAfterMs(),
                "unparseable value → null (the knob is never a failure surface)");
        assertNull(parse(503, null).getRetryAfterMs(), "no message → null");
    }

    @Test
    void overloaded529ClassifiesViaStatusHeuristic() {
        ChatResponse r = parse(529, "Overloaded");
        assertEquals(io.nop.ai.api.chat.ErrorClassification.OVERLOADED, r.getErrorClassification(),
                "529 → OVERLOADED (no config mapping — message classifier + heuristic path)");
        // "Overloaded" carries no retry hint → null (pure curve at policy level)
        assertNull(r.getRetryAfterMs());
    }

    @Test
    void messageClassifierClassifiesErrorTypeToken() {
        // errorType token (not message text) must still match — the classifier
        // input is the joined errorType+errorCode+errorMessage
        String body = "{\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}";
        ChatResponse r = dialect.parseErrorResponse(body, 500, null, plainConfig());
        assertEquals(io.nop.ai.api.chat.ErrorClassification.OVERLOADED, r.getErrorClassification(),
                "the overloaded_error errorType token classifies even on a 500 status");
    }

    @Test
    void quotaPatternIsGatedAbove500() {
        ChatResponse r = parse(500, "insufficient capacity");
        assertEquals(io.nop.ai.api.chat.ErrorClassification.TRANSIENT,
                r.getErrorClassification(),
                "5xx + QUOTA-row text must NOT divert into the account-chain channel (round 1 M6 gate)");
        ChatResponse r4xx = parse(400, "insufficient quota");
        assertEquals(io.nop.ai.api.chat.ErrorClassification.QUOTA_EXCEEDED,
                r4xx.getErrorClassification(),
                "4xx + QUOTA-row text classifies normally");
    }
}
