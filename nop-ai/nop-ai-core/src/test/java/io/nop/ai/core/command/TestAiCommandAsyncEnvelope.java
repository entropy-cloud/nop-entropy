package io.nop.ai.core.command;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;
import io.nop.ai.core.prompt.ChatExchange;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.SimplePromptTemplate;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.ai.core.NopAiCoreErrors.ARG_HTTP_STATUS;
import static io.nop.ai.core.NopAiCoreErrors.ARG_LLM_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carrier for 3.1-C3-005 (plan 2556 裁定 A): every prologue failure of
 * {@code AiCommand.callChat} — here the synchronous {@code ERR_AI_RATE_LIMITED}
 * throw from the chat service (the {@code ChatServiceImpl.checkRateLimit}
 * shape) — must be normalized into a failed future, never a synchronous throw
 * escaping the async envelope. {@code returnExceptionAsResponse} semantics are
 * unaffected.
 */
public class TestAiCommandAsyncEnvelope {

    /** Chat service that throws synchronously before returning a future. */
    static final class SyncThrowingChatService implements IChatService {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            calls.incrementAndGet();
            // The ChatServiceImpl.checkRateLimit shape: synchronous throw before
            // callAsync returns (httpStatus=429 → RATE_LIMITED classification).
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_RATE_LIMITED)
                    .param(ARG_LLM_NAME, "mock")
                    .param(ARG_HTTP_STATUS, 429);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }
    }

    private AiCommand newCommand(IChatService service) {
        IPromptTemplate template = SimplePromptTemplate.simplePrompt("test/envelope", "ping");
        AiCommand command = new AiCommand(service, null);
        command.promptTemplate(template);
        return command;
    }

    @Test
    public void rateLimitSyncThrowBecomesFailedFutureNotSyncThrow() {
        AiCommand command = newCommand(new SyncThrowingChatService());
        command.setReturnExceptionAsResponse(false);
        command.retryTimesPerRequest(0); // single attempt, no retry interference

        // The call itself must NOT throw synchronously.
        CompletableFuture<ChatExchange> future =
                command.executeAsync(java.util.Map.of(), null).toCompletableFuture();
        assertNotNull(future);

        // Single exception channel: CompletionException wrapping the rate-limit error.
        CompletionException e = assertThrows(CompletionException.class, future::join);
        Throwable cause = e.getCause();
        while (cause instanceof CompletionException && cause.getCause() != null)
            cause = cause.getCause();
        assertTrue(String.valueOf(cause.getMessage()).contains("rate-limited")
                        || cause instanceof NopAiCoreException,
                "the failed future must carry the rate-limit error, got: " + cause);
        assertEquals(1, ((SyncThrowingChatService) command.getChatService()).calls.get());
    }

    @Test
    public void returnExceptionAsResponsePathUnaffected() {
        AiCommand command = newCommand(new SyncThrowingChatService());
        // default returnExceptionAsResponse = true
        assertTrue(command.isReturnExceptionAsResponse());

        ChatExchange res = command.execute(java.util.Map.of(), null);
        assertNotNull(res, "with returnExceptionAsResponse the failure must surface as an invalid exchange");
        assertTrue(res.isInvalid());
        assertNotNull(res.getInvalidReason());
        assertEquals("nop.err.ai.service.rate-limited", res.getInvalidReason().getErrorCode());
        assertFalse(res.isValid());
    }

    @Test
    public void normalResponseStillValid() {
        AtomicInteger calls = new AtomicInteger();
        IChatService service = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(
                        ChatResponse.success(new ChatAssistantMessage("ok")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                throw new UnsupportedOperationException();
            }
        };
        AiCommand command = newCommand(service);
        ChatExchange res = command.execute(java.util.Map.of(), null);
        assertTrue(res.isValid());
        assertEquals("ok", res.getContent());
        assertEquals(1, calls.get());
    }
}
