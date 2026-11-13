package io.nop.ai.core.command;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;
import io.nop.ai.core.prompt.ChatExchange;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.SimplePromptTemplate;
import io.nop.ai.core.reliability.RetryContext;
import io.nop.ai.core.reliability.RetryOutcome;
import io.nop.ai.core.reliability.IRetryPolicy;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.ai.core.NopAiCoreErrors.ARG_HTTP_STATUS;
import static io.nop.ai.core.NopAiCoreErrors.ARG_LLM_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carrier for 3.1-C7-006 (plan 2556 裁定 H): the AiCommand retry loop is
 * exception-aware — an exceptionally-completed attempt (e.g. the rate-limit
 * failed future after 裁定 A) is classified via {@code LlmErrorClassifier} and
 * retried after a non-zero policy delay, with the retry unit being the whole
 * {@code callChat} (rate limiter naturally rechecked per attempt). Invalid
 * normal returns map to TRANSIENT for delay purposes; the attempt cap is
 * respected; non-retryable classifications fail fast.
 */
public class TestAiCommandRetryBackoff {

    /** Fixed-delay policy: retry the first retryable-classified failure after exactly 150ms. */
    static final class FixedDelayPolicy implements IRetryPolicy {
        @Override
        public RetryOutcome shouldRetry(RetryContext context) {
            ErrorClassification c = context.getErrorClassification();
            if (context.getAttempt() == 0
                    && (c == ErrorClassification.TRANSIENT || c == ErrorClassification.RATE_LIMITED)) {
                return RetryOutcome.retryAfter(150L);
            }
            return RetryOutcome.stop();
        }
    }

    /**
     * Scripted chat service: each call consumes the next script entry. An
     * exception entry is thrown synchronously (envelope-normalized by
     * callChat), a ChatResponse entry completes normally.
     */
    static final class ScriptedChatService implements IChatService {
        final java.util.Queue<Object> script = new java.util.ArrayDeque<>();
        final AtomicInteger calls = new AtomicInteger();
        final java.util.List<Long> callTimestamps = new java.util.ArrayList<>();

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            synchronized (callTimestamps) {
                callTimestamps.add(System.nanoTime());
            }
            calls.incrementAndGet();
            Object next = script.poll();
            if (next instanceof RuntimeException) {
                throw (RuntimeException) next;
            }
            return CompletableFuture.completedFuture((ChatResponse) next);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }
    }

    private static ChatResponse text(String content) {
        return ChatResponse.success(new ChatAssistantMessage(content));
    }

    private static Throwable rateLimitError() {
        return new NopAiCoreException(NopAiCoreErrors.ERR_AI_RATE_LIMITED)
                .param(ARG_LLM_NAME, "mock")
                .param(ARG_HTTP_STATUS, 429);
    }

    private static Throwable notFoundError() {
        return new NopAiCoreException(NopAiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR)
                .param(ARG_LLM_NAME, "mock")
                .param(ARG_HTTP_STATUS, 404);
    }

    private AiCommand newCommand(IChatService service) {
        IPromptTemplate template = SimplePromptTemplate.simplePrompt("test/retry-backoff", "ping");
        AiCommand command = new AiCommand(service, null);
        command.promptTemplate(template);
        command.setRetryPolicy(new FixedDelayPolicy());
        return command;
    }

    private static long firstCallSpan(ScriptedChatService service) {
        synchronized (service.callTimestamps) {
            return (service.callTimestamps.get(1) - service.callTimestamps.get(0)) / 1_000_000L;
        }
    }

    @Test
    public void retryableTransientFailureRetriesAfterNonZeroDelay() {
        ScriptedChatService service = new ScriptedChatService();
        service.script.add(new RuntimeException("injected transient network blip")); // classify → TRANSIENT
        service.script.add(text("recovered"));

        AiCommand command = newCommand(service);
        command.retryTimesPerRequest(2);

        long start = System.nanoTime();
        ChatExchange res = command.execute(Map.of(), null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(res.isValid());
        assertEquals("recovered", res.getContent());
        assertEquals(2, service.calls.get(), "an exceptionally-completed attempt must be retried");
        long span = firstCallSpan(service);
        assertTrue(span >= 140L, "the retry must wait the policy delay (got " + span + "ms between calls)");
        assertTrue(elapsedMs >= 140L);
    }

    @Test
    public void rateLimitedFirstAttemptClassifiedAndRetried() {
        ScriptedChatService service = new ScriptedChatService();
        service.script.add(rateLimitError()); // httpStatus=429 → LlmErrorClassifier RATE_LIMITED
        service.script.add(text("after-rate-limit"));

        AiCommand command = newCommand(service);
        command.retryTimesPerRequest(2);

        ChatExchange res = command.execute(Map.of(), null);

        assertTrue(res.isValid());
        assertEquals("after-rate-limit", res.getContent());
        assertEquals(2, service.calls.get(),
                "a RATE_LIMITED classified failure must be retried (exception-aware loop, 裁定 H)");
        assertTrue(firstCallSpan(service) >= 140L,
                "rate-limited retry must observe the policy delay");
    }

    @Test
    public void invalidNormalReturnTriggersTransientRetry() {
        ScriptedChatService service = new ScriptedChatService();
        service.script.add(text("")); // success-but-empty → marked invalid → TRANSIENT for delay purposes
        service.script.add(text("recovered-after-empty"));

        AiCommand command = newCommand(service);
        command.retryTimesPerRequest(2);

        ChatExchange res = command.execute(Map.of(), null);

        assertTrue(res.isValid());
        assertEquals("recovered-after-empty", res.getContent());
        assertEquals(2, service.calls.get());
        assertTrue(firstCallSpan(service) >= 140L,
                "invalid normal return must wait the TRANSIENT delay before retrying");
    }

    @Test
    public void attemptCapIsRespected() {
        ScriptedChatService service = new ScriptedChatService();
        // Always failing retryably — the cap must stop the loop.
        AiCommand command = newCommand(service);
        command.retryTimesPerRequest(1); // 1 retry hop → exactly 2 total attempts
        command.chatOptions(Map.of("provider", "mock", "model", "m"));

        // Script of 5 failures — more than the cap would allow if respected.
        for (int i = 0; i < 5; i++)
            service.script.add(new RuntimeException("injected transient failure " + i));

        long start = System.nanoTime();
        ChatExchange res = command.execute(Map.of(), null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(2, service.calls.get(), "retryTimesPerRequest=1 must cap at exactly 2 attempts");
        assertTrue(res.isInvalid(), "exhausted retries must surface the terminal invalid receipt");
        assertTrue(elapsedMs >= 140L, "the single allowed retry hop must still observe the delay");
    }

    @Test
    public void nonRetryableClassificationFailsFast() {
        ScriptedChatService service = new ScriptedChatService();
        service.script.add(notFoundError()); // httpStatus=404 → NON_TRANSIENT
        service.script.add(text("should-never-happen"));

        AiCommand command = newCommand(service);
        command.retryTimesPerRequest(3);

        ChatExchange res = command.execute(Map.of(), null);

        assertEquals(1, service.calls.get(), "a NON_TRANSIENT classification must fail fast (no retry)");
        assertTrue(res.isInvalid());
    }
}
