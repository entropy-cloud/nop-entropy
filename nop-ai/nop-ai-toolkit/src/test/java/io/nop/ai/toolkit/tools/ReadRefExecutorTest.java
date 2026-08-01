package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.ICompactionArchiveReader;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.compact.ShortRefHasher;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ReadRefExecutor}: hash-match read-back (success),
 * hash-mismatch / missing-reference / no-archive fail-loud paths
 * (design §8.2 Decision E + Minimum Rules #24).
 */
public class ReadRefExecutorTest {

    private final ReadRefExecutor executor = new ReadRefExecutor();
    private final InMemoryArchive archive = new InMemoryArchive();

    @Test
    void toolNameIsReadRef() {
        assertEquals("read-ref", executor.getToolName());
    }

    @Test
    void hashMatchReturnsOriginalContent() {
        String original = "line1\nline2\nline3\n".repeat(50);
        String hash = archive.put(original);

        AiToolCall call = callWith(hash, "file", "/some/path", "1-150");
        AiToolCallResult result = executor.executeAsync(call, ctxWith(archive))
                .toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        assertEquals(original, result.getOutput().getBody(),
                "on hash match the exact original content must be returned");
        assertNull(result.getError());
    }

    @Test
    void hashMismatchFailsLoud() {
        // Simulate a corrupted/substituted archive: store DIFFERENT content
        // under a hash that does not match it. The integrity check must
        // detect this and fail loud (defensive against archive corruption).
        String archivedContent = "archived-original-content";
        String realHashOfArchived = ShortRefHasher.hash(archivedContent);
        String claimedHash = ShortRefHasher.hash("different-content-that-was-substituted");
        archive.putCorrupt(claimedHash, archivedContent);

        AiToolCall call = callWith(claimedHash, "file", null, null);
        AiToolCallResult result = executor.executeAsync(call, ctxWith(archive))
                .toCompletableFuture().join();

        assertEquals("failure", result.getStatus(),
                "hash mismatch must fail loud, not return content");
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("integrity check FAILED"),
                "error should explain the integrity failure: " + result.getError().getBody());
        assertTrue(result.getError().getBody().contains(claimedHash));
        // sanity: the archived content's real hash differs from the claimed hash
        assertTrue(!realHashOfArchived.equals(claimedHash));
    }

    @Test
    void missingReferenceFailsLoud() {
        // Request a hash that was never archived
        String unknownHash = ShortRefHasher.hash("never-archived-content");

        AiToolCall call = callWith(unknownHash, null, null, null);
        AiToolCallResult result = executor.executeAsync(call, ctxWith(archive))
                .toCompletableFuture().join();

        assertEquals("failure", result.getStatus(),
                "missing reference must fail loud, not return empty/stale");
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("reference invalid"),
                "error should explain the reference is invalid: " + result.getError().getBody());
        assertTrue(result.getError().getBody().contains(unknownHash));
    }

    @Test
    void nullArchiveFailsLoud() {
        // AgentToolExecuteContext returns null when no archive materialised
        AiToolCall call = callWith(ShortRefHasher.hash("anything"), null, null, null);
        AiToolCallResult result = executor.executeAsync(call, ctxWithReader(null))
                .toCompletableFuture().join();

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("no compaction archive is available"),
                "null archive must surface explicit error: " + result.getError().getBody());
    }

    @Test
    void unsupportedArchiveFailsLoud() {
        // Default UOE bridge context (not the agent engine)
        AiToolCall call = callWith(ShortRefHasher.hash("anything"), null, null, null);
        AiToolCallResult result = executor.executeAsync(call, ctxThrowingUoe())
                .toCompletableFuture().join();

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("read-ref is not available"),
                "UOE context must surface explicit error: " + result.getError().getBody());
    }

    @Test
    void missingHashArgumentFailsLoud() {
        XNode node = XNode.make("read-ref");
        node.setAttr("id", "1");
        // no hash attr
        AiToolCall call = AiToolCall.fromNode(node);

        AiToolCallResult result = executor.executeAsync(call, ctxWith(archive))
                .toCompletableFuture().join();

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("'hash' argument"),
                "missing hash must be rejected: " + result.getError().getBody());
    }

    @Test
    void roundTripWithShortRefMarker() {
        // End-to-end of the shortRef -> read-ref data contract (tool level):
        // the hash carried by a serialized ShortRef is exactly what read-ref
        // consumes, and the read-back matches the original.
        String original = "config: value=42\n".repeat(30);
        String hash = archive.put(original);
        // Simulate the shortRef marker the LLM would see
        String marker = "[SHORT_REF type=file path=/app/config range=1-30 hash=" + hash + "]";
        assertTrue(marker.contains(hash));

        AiToolCall call = callWith(hash, "file", "/app/config", "1-30");
        AiToolCallResult result = executor.executeAsync(call, ctxWith(archive))
                .toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        assertEquals(original, result.getOutput().getBody());
    }

    // ---- helpers ----

    private AiToolCall callWith(String hash, String type, String path, String range) {
        XNode node = XNode.make("read-ref");
        node.setAttr("id", "1");
        node.setAttr("hash", hash);
        if (type != null) node.setAttr("type", type);
        if (path != null) node.setAttr("path", path);
        if (range != null) node.setAttr("range", range);
        return AiToolCall.fromNode(node);
    }

    private IToolExecuteContext ctxWith(InMemoryArchive archive) {
        return ctxWithReader(archive);
    }

    private IToolExecuteContext ctxWithReader(ICompactionArchiveReader reader) {
        return new ContextWithReader(reader);
    }

    private IToolExecuteContext ctxThrowingUoe() {
        return new IToolExecuteContext() {
            @Override public File getWorkDir() { return new File("."); }
            @Override public Map<String, String> getEnvs() { return Map.of(); }
            @Override public long getExpireAt() { return Long.MAX_VALUE; }
            @Override public ICancelToken getCancelToken() { return null; }
            @Override public io.nop.ai.toolkit.fs.IToolFileSystem getFileSystem() { return null; }
            @Override public IThreadPoolExecutor getExecutor() { return SyncThreadPoolExecutor.INSTANCE; }
            @Override public ICompactionArchiveReader getCompactionArchiveReader() {
                throw new UnsupportedOperationException("getCompactionArchiveReader is not available");
            }
        };
    }

    /**
     * Minimal in-memory archive for the read side: put computes the hash
     * (so the test seeds both the content and its hash key), getByHash reads
     * it back. Implements only the reader-side surface plus a test put.
     */
    static final class InMemoryArchive implements ICompactionArchiveReader {
        private final Map<String, String> store = new HashMap<>();

        String put(String content) {
            String hash = ShortRefHasher.hash(content);
            store.put(hash, content);
            return hash;
        }

        /**
         * Store content under an arbitrary (possibly mismatched) hash key,
         * simulating a corrupted or substituted archive so the read-ref
         * integrity check path can be exercised.
         */
        void putCorrupt(String hash, String content) {
            store.put(hash, content);
        }

        @Override
        public String getByHash(String hash) {
            return store.get(hash);
        }

        @Override
        public boolean contains(String hash) {
            return store.containsKey(hash);
        }
    }

    static final class ContextWithReader implements IToolExecuteContext {
        private final ICompactionArchiveReader reader;

        ContextWithReader(ICompactionArchiveReader reader) {
            this.reader = reader;
        }

        @Override public File getWorkDir() { return new File("."); }
        @Override public Map<String, String> getEnvs() { return Map.of(); }
        @Override public long getExpireAt() { return Long.MAX_VALUE; }
        @Override public ICancelToken getCancelToken() { return null; }
        @Override public io.nop.ai.toolkit.fs.IToolFileSystem getFileSystem() { return null; }
        @Override public IThreadPoolExecutor getExecutor() { return SyncThreadPoolExecutor.INSTANCE; }
        @Override public ICompactionArchiveReader getCompactionArchiveReader() { return reader; }
    }
}
