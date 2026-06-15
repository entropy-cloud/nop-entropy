package io.nop.ai.agent.reliability;

import io.nop.ai.core.AiCoreErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopTimeoutException;

/**
 * Maps a {@code chatService.call(...)} failure to an
 * {@link ErrorClassification} (design {@code nop-ai-agent-llm-layer.md} §7.2
 * / plan 207 / L3-2).
 *
 * <p>The classification drives the {@link IRetryPolicy} decision:
 * {@link ErrorClassification#TRANSIENT} and
 * {@link ErrorClassification#RATE_LIMITED} are eligible for retry;
 * {@link ErrorClassification#NON_TRANSIENT} and
 * {@link ErrorClassification#QUOTA_EXCEEDED} fail fast.
 *
 * <h2>Classification rules</h2>
 * <ul>
 *   <li><b>Timeout</b> ({@link NopTimeoutException} or a JDK/IO timeout
 *       cause) → {@link ErrorClassification#TRANSIENT}.</li>
 *   <li><b>{@code NopException} carrying {@code ARG_HTTP_STATUS}</b>
 *       (the shape thrown by {@code ChatServiceImpl} /
 *       {@code DefaultAiChatService} on a non-200 provider response —
 *       {@code ERR_AI_SERVICE_HTTP_ERROR}):
 *     <ul>
 *       <li>429 → {@link ErrorClassification#RATE_LIMITED}. The current
 *           exception does not carry the {@code Retry-After} header
 *           (Non-Goal — the header is dropped by {@code ChatServiceImpl}),
 *           so the retry uses exponential backoff rather than
 *           header-driven wait. Distinguishing 429
 *           {@code rate_limit_exceeded} from 429
 *           {@code insufficient_quota} would require the response body
 *           (also dropped), so all 429s map to RATE_LIMITED here;
 *           QUOTA_EXCEEDED is a separate classification exposed on the
 *           enum for a successor that parses the body.</li>
 *       <li>5xx → {@link ErrorClassification#TRANSIENT} (server-side,
 *           transient).</li>
 *       <li>4xx (400/401/403/404/...) →
 *           {@link ErrorClassification#NON_TRANSIENT} (client-side;
 *           retrying the identical request fails identically).</li>
 *     </ul>
 *   </li>
 *   <li><b>Any other exception</b> (unknown runtime/network fault) →
 *       {@link ErrorClassification#TRANSIENT}. Unknown failures on a
 *       network call are assumed transient; a functional policy can still
 *       cap retries via max-attempts, so the cost of a wrong guess is
 *       bounded.</li>
 * </ul>
 *
 * <p>This classifier is intentionally a stateless static utility — there is
 * no per-call state, so it is inherently thread-safe and needs no instance.
 */
public final class LlmErrorClassifier {

    private LlmErrorClassifier() {
    }

    /**
     * Classify the given {@code error} (thrown by a failed
     * {@code chatService.call(...)}).
     *
     * @param error the failure; if {@code null}, returns
     *              {@link ErrorClassification#NON_TRANSIENT} (no error to
     *              classify → do not retry)
     * @return a non-null {@link ErrorClassification}; never {@code null}
     */
    public static ErrorClassification classify(Throwable error) {
        if (error == null) {
            return ErrorClassification.NON_TRANSIENT;
        }

        // Explicit timeout → transient.
        if (error instanceof NopTimeoutException) {
            return ErrorClassification.TRANSIENT;
        }
        // Walk the cause chain for timeout markers (the http client may
        // wrap a SocketTimeoutException / InterruptedIOException inside a
        // NopException or CompletionException).
        Throwable e = error;
        while (e != null) {
            String name = e.getClass().getName();
            if (name.equals("java.net.SocketTimeoutException")
                    || name.equals("java.io.InterruptedIOException")) {
                return ErrorClassification.TRANSIENT;
            }
            e = e.getCause();
        }

        // NopException carrying an HTTP status code → classify by code.
        Integer httpStatus = readHttpStatus(error);
        if (httpStatus != null) {
            int code = httpStatus;
            if (code == 429) {
                return ErrorClassification.RATE_LIMITED;
            }
            if (code >= 500 && code < 600) {
                return ErrorClassification.TRANSIENT;
            }
            if (code >= 400 && code < 500) {
                return ErrorClassification.NON_TRANSIENT;
            }
        }

        // Unknown failure on a network call → assume transient.
        return ErrorClassification.TRANSIENT;
    }

    /**
     * Read the {@code ARG_HTTP_STATUS} param from a {@link NopException}
     * (the param set by {@code ChatServiceImpl} /
     * {@code DefaultAiChatService} on a non-200 provider response). Returns
     * {@code null} when the error is not a {@link NopException} or does not
     * carry the param.
     */
    private static Integer readHttpStatus(Throwable error) {
        if (!(error instanceof NopException)) {
            return null;
        }
        Object value = ((NopException) error).getParam(AiCoreErrors.ARG_HTTP_STATUS);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
}
