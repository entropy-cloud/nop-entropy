package io.nop.ai.agent.reliability;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 unit tests for the checkpoint contract surface: {@link NoOpCheckpoint}
 * pass-through semantics + thread safety, and {@link Checkpoint} value-type
 * construction / immutability / factory validation.
 *
 * <p>These tests follow the {@code TestNoOpDenialLedger} sibling pattern.
 */
public class TestNoOpCheckpointAndValue {

    // ========================================================================
    // NoOpCheckpoint: pass-through semantics
    // ========================================================================

    @Test
    void noOpSaveCheckpointDoesNotThrowAndHasNoSideEffect() {
        ICheckpointManager mgr = NoOpCheckpoint.noOp();
        Checkpoint cp = sampleCheckpoint("sess", "wm-1", 0);

        // saveCheckpoint accepts any checkpoint without throwing and without
        // side effect (a subsequent getLatestCheckpoint still returns null).
        assertDoesNotThrow(() -> mgr.saveCheckpoint(cp));
        assertNull(mgr.getLatestCheckpoint("sess"),
                "NoOpCheckpoint must not store anything — getLatestCheckpoint must be null even after saveCheckpoint");
    }

    @Test
    void noOpGetLatestCheckpointReturnsNullEvenAfterSave() {
        ICheckpointManager mgr = NoOpCheckpoint.noOp();
        mgr.saveCheckpoint(sampleCheckpoint("sess", "wm-1", 0));
        mgr.saveCheckpoint(sampleCheckpoint("sess", "wm-2", 1));

        assertNull(mgr.getLatestCheckpoint("sess"),
                "NoOpCheckpoint.getLatestCheckpoint must always return null");
        assertNull(mgr.getLatestCheckpoint(null),
                "NoOpCheckpoint.getLatestCheckpoint(null) must return null");
    }

    @Test
    void noOpGetCheckpointReturnsNullEvenAfterSave() {
        ICheckpointManager mgr = NoOpCheckpoint.noOp();
        mgr.saveCheckpoint(sampleCheckpoint("sess", "wm-1", 0));

        assertNull(mgr.getCheckpoint("wm-1"),
                "NoOpCheckpoint.getCheckpoint must always return null");
        assertNull(mgr.getCheckpoint("nonexistent"),
                "NoOpCheckpoint.getCheckpoint for a nonexistent watermark must return null");
    }

    @Test
    void noOpFactoryReturnsSingletonInstance() {
        ICheckpointManager a = NoOpCheckpoint.noOp();
        ICheckpointManager b = NoOpCheckpoint.noOp();
        assertTrue(a == b, "NoOpCheckpoint.noOp() must return the same singleton instance");
        assertTrue(a instanceof NoOpCheckpoint);
    }

    // ========================================================================
    // NoOpCheckpoint: thread safety (concurrent calls from multiple threads)
    // ========================================================================

    @Test
    void noOpIsThreadSafeUnderConcurrentAccess() throws InterruptedException {
        ICheckpointManager mgr = NoOpCheckpoint.noOp();
        int threads = 8;
        int callsPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            final int tid = i;
            pool.submit(() -> {
                try {
                    for (int j = 0; j < callsPerThread; j++) {
                        mgr.saveCheckpoint(sampleCheckpoint(
                                "sess-" + tid, "wm-" + tid + "-" + j, j));
                        // Interspersed reads must not throw.
                        mgr.getLatestCheckpoint("sess-" + tid);
                        mgr.getCheckpoint("wm-" + tid + "-" + j);
                    }
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS),
                "All concurrent threads must complete within the timeout");
        pool.shutdownNow();

        assertNull(error.get(),
                "NoOpCheckpoint must be thread-safe under concurrent save/get calls: " + error.get());
        // Even after heavy concurrent saves, the NoOp must not have stored anything.
        assertNull(mgr.getLatestCheckpoint("sess-0"),
                "NoOpCheckpoint must remain stateless after concurrent access");
    }

    // ========================================================================
    // Checkpoint value type: construction, field access, immutability
    // ========================================================================

    @Test
    void checkpointConstructionAndFieldAccess() {
        Checkpoint cp = Checkpoint.of(
                "sess-1", "wm-1", 3, 1700000000L,
                CheckpointType.TOOL_EXECUTION, "shell.exec", "call-1",
                "ls -la", "output-ok", 42, 1024L);

        assertEquals("sess-1", cp.getSessionId());
        assertEquals("wm-1", cp.getWatermark());
        assertEquals(3, cp.getSeq());
        assertEquals(1700000000L, cp.getTimestamp());
        assertEquals(CheckpointType.TOOL_EXECUTION, cp.getType());
        assertEquals("shell.exec", cp.getToolName());
        assertEquals("call-1", cp.getCallId());
        assertEquals("ls -la", cp.getInputSummary());
        assertEquals("output-ok", cp.getOutputSummary());
        assertEquals(42, cp.getMessageCount());
        assertEquals(1024L, cp.getTokenEstimate());
    }

    @Test
    void checkpointAllowsNullableFieldsForNonToolTypes() {
        Checkpoint cp = Checkpoint.of(
                "sess-1", "wm-llm", 0, 1700000001L,
                CheckpointType.LLM_TURN, null, null, null, null, 5, 500L);

        assertEquals(CheckpointType.LLM_TURN, cp.getType());
        assertNull(cp.getToolName());
        assertNull(cp.getCallId());
    }

    @Test
    void checkpointAllowsNullSessionId() {
        Checkpoint cp = Checkpoint.of(
                null, "wm-anon", 0, 1700000002L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-x",
                null, null, 1, 10L);

        assertNull(cp.getSessionId());
        assertEquals("wm-anon", cp.getWatermark());
    }

    @Test
    void checkpointEqualsAndHashCodeUseAllFields() {
        Checkpoint a = Checkpoint.of(
                "sess", "wm", 1, 100L, CheckpointType.TOOL_EXECUTION,
                "tool", "call", "in", "out", 5, 100L);
        Checkpoint b = Checkpoint.of(
                "sess", "wm", 1, 100L, CheckpointType.TOOL_EXECUTION,
                "tool", "call", "in", "out", 5, 100L);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        // Differ in any one field → not equal
        assertNotEquals(a, Checkpoint.of("other", "wm", 1, 100L, CheckpointType.TOOL_EXECUTION,
                "tool", "call", "in", "out", 5, 100L));
        assertNotEquals(a, Checkpoint.of("sess", "wm-other", 1, 100L, CheckpointType.TOOL_EXECUTION,
                "tool", "call", "in", "out", 5, 100L));
        assertNotEquals(a, Checkpoint.of("sess", "wm", 2, 100L, CheckpointType.TOOL_EXECUTION,
                "tool", "call", "in", "out", 5, 100L));
        assertNotEquals(a, Checkpoint.of("sess", "wm", 1, 999L, CheckpointType.TOOL_EXECUTION,
                "tool", "call", "in", "out", 5, 100L));
        assertNotEquals(a, Checkpoint.of("sess", "wm", 1, 100L, CheckpointType.LLM_TURN,
                "tool", "call", "in", "out", 5, 100L));
        assertNotEquals(a, Checkpoint.of("sess", "wm", 1, 100L, CheckpointType.TOOL_EXECUTION,
                "other", "call", "in", "out", 5, 100L));
        assertNotEquals(a, Checkpoint.of("sess", "wm", 1, 100L, CheckpointType.TOOL_EXECUTION,
                "tool", "other", "in", "out", 5, 100L));
        assertNotEquals(a, Checkpoint.of("sess", "wm", 1, 100L, CheckpointType.TOOL_EXECUTION,
                "tool", "call", "other", "out", 5, 100L));
        assertNotEquals(a, Checkpoint.of("sess", "wm", 1, 100L, CheckpointType.TOOL_EXECUTION,
                "tool", "call", "in", "other", 5, 100L));
        assertNotEquals(a, Checkpoint.of("sess", "wm", 1, 100L, CheckpointType.TOOL_EXECUTION,
                "tool", "call", "in", "out", 99, 100L));
        assertNotEquals(a, Checkpoint.of("sess", "wm", 1, 100L, CheckpointType.TOOL_EXECUTION,
                "tool", "call", "in", "out", 5, 999L));
    }

    @Test
    void checkpointToStringContainsKeyFields() {
        Checkpoint cp = Checkpoint.of(
                "sess-1", "wm-1", 0, 100L, CheckpointType.TOOL_EXECUTION,
                "shell", "call", null, null, 2, 50L);
        String s = cp.toString();
        assertTrue(s.contains("sess-1"));
        assertTrue(s.contains("wm-1"));
        assertTrue(s.contains("TOOL_EXECUTION"));
    }

    // ========================================================================
    // Checkpoint value type: factory validation
    // ========================================================================

    @Test
    void checkpointFactoryRejectsNullWatermark() {
        assertThrows(IllegalArgumentException.class, () ->
                Checkpoint.of("sess", null, 0, 1L, CheckpointType.TOOL_EXECUTION,
                        "tool", "call", null, null, 0, 0L));
    }

    @Test
    void checkpointFactoryRejectsNullType() {
        assertThrows(IllegalArgumentException.class, () ->
                Checkpoint.of("sess", "wm", 0, 1L, null,
                        "tool", "call", null, null, 0, 0L));
    }

    @Test
    void checkpointFactoryRejectsNegativeSeq() {
        assertThrows(IllegalArgumentException.class, () ->
                Checkpoint.of("sess", "wm", -1, 1L, CheckpointType.TOOL_EXECUTION,
                        "tool", "call", null, null, 0, 0L));
    }

    @Test
    void checkpointFactoryRejectsNegativeMessageCount() {
        assertThrows(IllegalArgumentException.class, () ->
                Checkpoint.of("sess", "wm", 0, 1L, CheckpointType.TOOL_EXECUTION,
                        "tool", "call", null, null, -1, 0L));
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Checkpoint sampleCheckpoint(String sessionId, String watermark, int seq) {
        return Checkpoint.of(sessionId, watermark, seq, System.currentTimeMillis(),
                CheckpointType.TOOL_EXECUTION, "echo", "call-" + watermark,
                "in", "out", seq + 1, 100L * (seq + 1));
    }
}
