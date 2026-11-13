package io.nop.ai.core.command;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.prompt.ChatExchange;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.SimplePromptTemplate;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carrier for 3.1-C3-006 (plan 2556 裁定 B): response-cache read/write
 * failures are symmetrically isolated — a failing cache save must NOT turn an
 * already-successful response exceptional, and a failing cache load degrades
 * to a cache miss (live call proceeds).
 */
public class TestAiCommandCacheFailureIsolation {

    static IChatService okService(AtomicInteger calls) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(
                        ChatResponse.success(new ChatAssistantMessage("live answer")));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private AiCommand newCommand(IChatService service, io.nop.ai.core.persist.IAiChatResponseCache cache) {
        IPromptTemplate template = SimplePromptTemplate.simplePrompt("test/cache-iso", "ping");
        AiCommand command = new AiCommand(service, null);
        command.promptTemplate(template);
        command.setChatResponseCache(cache);
        return command;
    }

    /** Cache whose save always throws (disk full / IO failure shape). */
    static final class FailingSaveCache implements io.nop.ai.core.persist.IAiChatResponseCache {
        int saveAttempts;

        @Override
        public ChatExchange loadCachedResponse(ChatRequest request, ChatOptions options, String promptName) {
            return null; // always a miss
        }

        @Override
        public void saveCachedResponse(ChatExchange exchange, String promptName) {
            saveAttempts++;
            throw new IllegalStateException("injected disk-full failure");
        }
    }

    /** Cache whose load always throws (corrupt file shape). */
    static final class FailingLoadCache implements io.nop.ai.core.persist.IAiChatResponseCache {
        int loadAttempts;

        @Override
        public ChatExchange loadCachedResponse(ChatRequest request, ChatOptions options, String promptName) {
            loadAttempts++;
            throw new IllegalStateException("injected corrupt-cache failure");
        }

        @Override
        public void saveCachedResponse(ChatExchange exchange, String promptName) {
            // no-op success
        }
    }

    @Test
    public void failingCacheSaveDoesNotFailSuccessfulResponse() {
        AtomicInteger calls = new AtomicInteger();
        FailingSaveCache cache = new FailingSaveCache();
        AiCommand command = newCommand(okService(calls), cache);

        ChatExchange res = command.execute(Map.of(), null);

        assertTrue(res.isValid(), "a cache-write failure must not make the successful response exceptional");
        assertEquals("live answer", res.getContent());
        assertEquals(1, calls.get());
        assertEquals(1, cache.saveAttempts, "the save must have been attempted (then isolated)");
    }

    @Test
    public void failingCacheLoadDegradesToMissAndLiveCallProceeds() {
        AtomicInteger calls = new AtomicInteger();
        FailingLoadCache cache = new FailingLoadCache();
        AiCommand command = newCommand(okService(calls), cache);

        ChatExchange res = command.execute(Map.of(), null);

        assertTrue(res.isValid(), "a cache-read failure must degrade to a miss, not fail the call");
        assertEquals("live answer", res.getContent());
        assertEquals(1, cache.loadAttempts);
        assertEquals(1, calls.get(), "the live call must still happen after the load failure");
    }
}
