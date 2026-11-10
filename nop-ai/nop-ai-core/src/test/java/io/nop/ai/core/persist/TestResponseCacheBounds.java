package io.nop.ai.core.persist;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.NopAiCoreException;
import io.nop.ai.core.prompt.ChatExchange;
import io.nop.commons.util.FileHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carrier for 3.1-C5-002 (plan 2556 裁定 D) + CL-041: the response cache disk
 * envelope — max-entries LRU eviction deletes the evicted file, oversize
 * entries are skipped on write (and treated as misses on load), and prompt
 * names with path-traversal components are rejected with a typed error.
 */
public class TestResponseCacheBounds {

    private File cacheDir;
    private DefaultAiChatResponseCache cache;

    @BeforeEach
    public void setUp() throws Exception {
        cacheDir = File.createTempFile("ai-response-cache-bounds-test", "");
        assertTrue(cacheDir.delete());
        assertTrue(cacheDir.mkdirs());
        cache = new DefaultAiChatResponseCache();
        cache.setCacheDir(cacheDir.getPath());
        cache.setChatExchangePersister(new DefaultChatExchangePersister());
    }

    @AfterEach
    public void tearDown() {
        if (cacheDir != null)
            FileHelper.deleteAll(cacheDir);
    }

    private ChatExchange newExchange(String content) {
        ChatRequest request = new ChatRequest();
        request.addUserPrompt(content);
        ChatOptions options = new ChatOptions();
        options.setProvider("test-provider");
        options.setModel("test-model");
        request.setOptions(options);
        ChatExchange exchange = new ChatExchange(request, null);
        exchange.setContent("answer-" + content);
        return exchange;
    }

    @Test
    public void maxEntriesLruEvictionDeletesFile() {
        cache.setMaxEntries(2);

        ChatExchange e1 = newExchange("one");
        ChatExchange e2 = newExchange("two");
        ChatExchange e3 = newExchange("three");
        cache.saveCachedResponse(e1, "test/bounds");
        cache.saveCachedResponse(e2, "test/bounds");
        File f1 = cache.getCacheResource(e1.getRequest(), e1.getRequest().makeOptions(), "test/bounds").toFile();
        File f2 = cache.getCacheResource(e2.getRequest(), e2.getRequest().makeOptions(), "test/bounds").toFile();
        assertTrue(f1.exists(), "first entry must exist within the bound");
        assertTrue(f2.exists());

        // Third save exceeds maxEntries=2 → LRU evicts e1's file.
        cache.saveCachedResponse(e3, "test/bounds");
        File f3 = cache.getCacheResource(e3.getRequest(), e3.getRequest().makeOptions(), "test/bounds").toFile();

        assertFalse(f1.exists(), "the LRU-evicted entry's cache file must be deleted");
        assertTrue(f2.exists(), "the recently-used entry must survive");
        assertTrue(f3.exists(), "the newest entry must survive");
        assertEquals(2, cache.getMaxEntries());
    }

    @Test
    public void oversizeEntrySkippedOnWrite() {
        cache.setMaxEntryBytes(64);

        ChatExchange big = newExchange("this content is far longer than sixty-four bytes of serialized json");
        cache.saveCachedResponse(big, "test/bounds");
        File f = cache.getCacheResource(big.getRequest(), big.getRequest().makeOptions(), "test/bounds").toFile();
        assertFalse(f.exists(), "an entry over cache-max-entry-bytes must be skipped (never written)");

        // And it must not be loadable from a (nonexistent) file either.
        assertNull(cache.loadCachedResponse(big.getRequest(), big.getRequest().makeOptions(), "test/bounds"));
    }

    @Test
    public void oversizeEntryTreatedAsMissAndDeletedOnLoad() {
        // Save with a generous cap, then shrink the cap: the existing oversize
        // file must be treated as a miss and deleted on the next load.
        cache.setMaxEntryBytes(0); // unbounded write
        ChatExchange exchange = newExchange("a reasonably sized cached answer");
        cache.saveCachedResponse(exchange, "test/bounds");
        File f = cache.getCacheResource(exchange.getRequest(), exchange.getRequest().makeOptions(), "test/bounds").toFile();
        assertTrue(f.exists());

        cache.setMaxEntryBytes(16);
        assertNull(cache.loadCachedResponse(exchange.getRequest(),
                exchange.getRequest().makeOptions(), "test/bounds"),
                "an existing file over the read cap must be a miss");
        assertFalse(f.exists(), "the oversize entry must be deleted on the lazy read-cap miss");
    }

    @Test
    public void cl041PathTraversalPromptNameRejected() {
        ChatExchange exchange = newExchange("traversal");
        assertThrows(NopAiCoreException.class,
                () -> cache.saveCachedResponse(exchange, "../evil"));
        assertThrows(NopAiCoreException.class,
                () -> cache.saveCachedResponse(exchange, "ns/../../escape"));
        assertThrows(NopAiCoreException.class,
                () -> cache.saveCachedResponse(exchange, "/absolute/path"));
        assertThrows(NopAiCoreException.class,
                () -> cache.saveCachedResponse(exchange, "back\\slash"));
        assertThrows(NopAiCoreException.class,
                () -> cache.loadCachedResponse(exchange.getRequest(),
                        exchange.getRequest().makeOptions(), "a/./b"));
        // Nothing must have been written under a traversal path.
        assertTrue(countFiles(cacheDir) == 0);
    }

    @Test
    public void namespacedPromptNameStillAllowed() {
        ChatExchange exchange = newExchange("namespaced");
        cache.saveCachedResponse(exchange, "test/prompt"); // legacy "ns/name" shape must keep working
        ChatExchange loaded = cache.loadCachedResponse(exchange.getRequest(),
                exchange.getRequest().makeOptions(), "test/prompt");
        assertNotNull(loaded, "a namespaced but traversal-free promptName must remain usable");
        assertEquals("answer-namespaced", loaded.getContent());
    }

    private static int countFiles(File dir) {
        File[] children = dir.listFiles();
        if (children == null)
            return 0;
        int n = 0;
        for (File c : children) {
            if (c.isFile())
                n++;
            else
                n += countFiles(c);
        }
        return n;
    }
}
