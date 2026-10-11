package io.nop.ai.core.persist;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.prompt.ChatExchange;
import io.nop.commons.util.FileHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 regression tests for the response cache over the new stack: save/load
 * round-trip through {@link DefaultChatExchangePersister} (JSON + optional AES)
 * and lazy TTL expiry in {@link DefaultAiChatResponseCache}.
 */
public class TestAiChatResponseCacheTtl {

    private File cacheDir;
    private DefaultAiChatResponseCache cache;

    @BeforeEach
    public void setUp() throws Exception {
        cacheDir = File.createTempFile("ai-response-cache-test", "");
        assertTrue(cacheDir.delete());
        assertTrue(cacheDir.mkdirs());
        cache = new DefaultAiChatResponseCache();
        cache.setCacheDir(cacheDir.getPath());
        DefaultChatExchangePersister persister = new DefaultChatExchangePersister();
        cache.setChatExchangePersister(persister);
    }

    @AfterEach
    public void tearDown() {
        if (cacheDir != null)
            FileHelper.deleteAll(cacheDir);
    }

    private ChatExchange newExchange(String content) {
        ChatRequest request = new ChatRequest();
        request.addUserPrompt("hello");
        ChatOptions options = new ChatOptions();
        options.setProvider("test-provider");
        options.setModel("test-model");
        request.setOptions(options);

        ChatExchange exchange = new ChatExchange(request, null);
        exchange.setContent(content);
        return exchange;
    }

    @Test
    public void testSaveLoadRoundTrip() {
        ChatExchange exchange = newExchange("cached answer");
        cache.saveCachedResponse(exchange, "test/prompt");

        ChatRequest sameRequest = exchange.getRequest();
        ChatExchange loaded = cache.loadCachedResponse(sameRequest,
                sameRequest.makeOptions(), "test/prompt");
        assertNotNull(loaded, "same request must hit the cache");
        assertEquals("cached answer", loaded.getContent());
    }

    @Test
    public void testDifferentRequestMisses() {
        cache.saveCachedResponse(newExchange("cached answer"), "test/prompt");

        ChatRequest other = new ChatRequest();
        other.addUserPrompt("different prompt");
        ChatOptions options = new ChatOptions();
        options.setProvider("test-provider");
        options.setModel("test-model");
        other.setOptions(options);

        assertNull(cache.loadCachedResponse(other, options, "test/prompt"),
                "different request content must miss the cache");
    }

    @Test
    public void testTtlExpiryDeletesEntry() throws Exception {
        cache.setCacheTtlSeconds(3600L);
        ChatExchange exchange = newExchange("expired answer");
        cache.saveCachedResponse(exchange, "test/ttl");

        // backdate the cache file so its mtime is older than the ttl
        backdateCacheFiles();

        ChatExchange loaded = cache.loadCachedResponse(exchange.getRequest(),
                exchange.getRequest().makeOptions(), "test/ttl");
        assertNull(loaded, "entry older than ttl must be treated as a miss");
        assertNull(cache.loadCachedResponse(exchange.getRequest(),
                exchange.getRequest().makeOptions(), "test/ttl"),
                "expired entry must have been deleted after the first lazy miss");
    }

    private void backdateCacheFiles() {
        java.util.Deque<File> queue = new java.util.ArrayDeque<>();
        queue.add(cacheDir);
        long old = System.currentTimeMillis() - 7200_000L;
        while (!queue.isEmpty()) {
            File cur = queue.poll();
            File[] children = cur.listFiles();
            if (children != null)
                queue.addAll(java.util.List.of(children));
            if (cur.isFile())
                cur.setLastModified(old);
        }
    }

    @Test
    public void testPersisterRoundTripPreservesInvalidState() {
        DefaultChatExchangePersister persister = new DefaultChatExchangePersister();
        ChatExchange exchange = newExchange("text");
        exchange.setInvalid(true);
        exchange.setOutput("RESULT", 42);

        ChatExchange restored = persister.deserialize(persister.serialize(exchange));
        assertNotNull(restored);
        assertEquals(true, restored.isInvalid());
        assertEquals(42, restored.getOutput("RESULT"));
        assertEquals("text", restored.getContent());
    }
}
