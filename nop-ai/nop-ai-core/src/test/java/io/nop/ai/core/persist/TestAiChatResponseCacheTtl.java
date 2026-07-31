package io.nop.ai.core.persist;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA6.5-AR-7: cache TTL expiry for {@link DefaultAiChatResponseCache}.
 * Expiry is lazy (checked on read, based on the cache file mtime) and the
 * cache file format is never modified.
 */
public class TestAiChatResponseCacheTtl {

    private DefaultAiChatResponseCache newCache(long ttlSeconds, File cacheDir) throws Exception {
        DefaultAiChatResponseCache cache = new DefaultAiChatResponseCache();
        cache.setCacheDir(cacheDir.getAbsolutePath());
        cache.setCacheTtlSeconds(ttlSeconds);
        cache.setChatExchangePersister(DefaultAiChatExchangePersister.instance());
        return cache;
    }

    private AiChatExchange newExchange() {
        AiChatExchange exchange = new AiChatExchange();
        exchange.setExchangeId("ttl-test");
        exchange.setPrompt(Prompt.userText("cache me"));
        exchange.setContent("cached response");
        AiChatOptions options = exchange.makeChatOptions();
        options.setProvider("openai");
        options.setModel("gpt-test");
        exchange.setChatOptions(options);
        return exchange;
    }

    @Test
    public void testExpiredEntryIsMissAndDeleted() throws Exception {
        File cacheDir = Files.createTempDirectory("ai-cache-ttl").toFile();
        DefaultAiChatResponseCache cache = newCache(1, cacheDir);

        AiChatExchange exchange = newExchange();
        cache.saveCachedResponse(exchange);

        IResource resource = cache.getCacheResource(exchange.getPrompt(), exchange.getChatOptions());
        assertTrue(((FileResource) resource).toFile().exists());
        ((FileResource) resource).toFile().setLastModified(System.currentTimeMillis() - 3600_000L);

        AiChatExchange loaded = cache.loadCachedResponse(exchange.getPrompt(), exchange.getChatOptions());
        assertNull(loaded, "entry older than TTL must be treated as a cache miss");
        assertTrue(!((FileResource) resource).toFile().exists(),
                "expired cache entry must be deleted");
    }

    @Test
    public void testFreshEntryHits() throws Exception {
        File cacheDir = Files.createTempDirectory("ai-cache-fresh").toFile();
        DefaultAiChatResponseCache cache = newCache(3600, cacheDir);

        AiChatExchange exchange = newExchange();
        cache.saveCachedResponse(exchange);

        AiChatExchange loaded = cache.loadCachedResponse(exchange.getPrompt(), exchange.getChatOptions());
        assertNotNull(loaded, "fresh entry must be a cache hit");
        assertEquals("cached response", loaded.getContent());
    }

    @Test
    public void testZeroTtlNeverExpires() throws Exception {
        File cacheDir = Files.createTempDirectory("ai-cache-zero").toFile();
        DefaultAiChatResponseCache cache = newCache(0, cacheDir);

        AiChatExchange exchange = newExchange();
        cache.saveCachedResponse(exchange);

        IResource resource = cache.getCacheResource(exchange.getPrompt(), exchange.getChatOptions());
        ((FileResource) resource).toFile().setLastModified(System.currentTimeMillis() - 3600_000L);

        AiChatExchange loaded = cache.loadCachedResponse(exchange.getPrompt(), exchange.getChatOptions());
        assertNotNull(loaded, "TTL 0 (default) must never expire entries");
        assertTrue(((FileResource) resource).toFile().exists(),
                "default TTL must not delete cached entries");
    }
}
