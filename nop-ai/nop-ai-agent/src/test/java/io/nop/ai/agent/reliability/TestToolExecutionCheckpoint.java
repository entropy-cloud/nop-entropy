package io.nop.ai.agent.reliability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Phase 2 functional unit tests for {@link ToolExecutionCheckpoint}: verifies
 * the save→retrieve round-trip (the core value of the functional
 * implementation) and per-session isolation. This proves the contract is
 * non-hollow — the functional implementation actually stores and retrieves
 * checkpoints, unlike the {@link NoOpCheckpoint} pass-through default.
 *
 * <p>Follows the {@code DBDenialLedger} per-session independence test pattern
 * (but in-memory).
 */
public class TestToolExecutionCheckpoint {

    // ========================================================================
    // Save → retrieve round-trip (core value)
    // ========================================================================

    @Test
    void saveThenGetLatestReturnsTheCheckpoint() {
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint();
        Checkpoint cp = Checkpoint.of(
                "sess-1", "wm-1", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-1",
                "hi", "echo: hi", 3, 42L);

        mgr.saveCheckpoint(cp);

        Checkpoint latest = mgr.getLatestCheckpoint("sess-1");
        assertNotNull(latest, "getLatestCheckpoint must return the saved checkpoint");
        assertEquals("wm-1", latest.getWatermark());
        assertEquals(0, latest.getSeq());
        assertEquals(1000L, latest.getTimestamp());
        assertEquals(CheckpointType.TOOL_EXECUTION, latest.getType());
        assertEquals("echo", latest.getToolName());
        assertEquals("call-1", latest.getCallId());
        assertEquals("hi", latest.getInputSummary());
        assertEquals("echo: hi", latest.getOutputSummary());
        assertEquals(3, latest.getMessageCount());
        assertEquals(42L, latest.getTokenEstimate());
    }

    @Test
    void multipleSavesReturnLatestAsLastCheckpoint() {
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint();

        Checkpoint cp0 = Checkpoint.of("sess", "wm-0", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c0", null, "out-0", 1, 10L);
        Checkpoint cp1 = Checkpoint.of("sess", "wm-1", 1, 1001L,
                CheckpointType.TOOL_EXECUTION, "ls", "c1", null, "out-1", 2, 20L);
        Checkpoint cp2 = Checkpoint.of("sess", "wm-2", 2, 1002L,
                CheckpointType.TOOL_EXECUTION, "pwd", "c2", null, "out-2", 3, 30L);

        mgr.saveCheckpoint(cp0);
        assertEquals("wm-0", mgr.getLatestCheckpoint("sess").getWatermark());

        mgr.saveCheckpoint(cp1);
        assertEquals("wm-1", mgr.getLatestCheckpoint("sess").getWatermark());

        mgr.saveCheckpoint(cp2);
        Checkpoint latest = mgr.getLatestCheckpoint("sess");
        assertEquals("wm-2", latest.getWatermark());
        assertEquals(2, latest.getSeq());
        assertEquals("pwd", latest.getToolName());
    }

    @Test
    void getCheckpointByWatermarkReturnsExactMatch() {
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint();

        mgr.saveCheckpoint(Checkpoint.of("sess", "wm-a", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c-a", null, "out-a", 1, 10L));
        mgr.saveCheckpoint(Checkpoint.of("sess", "wm-b", 1, 1001L,
                CheckpointType.TOOL_EXECUTION, "ls", "c-b", null, "out-b", 2, 20L));

        Checkpoint a = mgr.getCheckpoint("wm-a");
        assertNotNull(a);
        assertEquals("echo", a.getToolName());

        Checkpoint b = mgr.getCheckpoint("wm-b");
        assertNotNull(b);
        assertEquals("ls", b.getToolName());

        assertNotEquals(a, b, "getCheckpoint must return the exact match, not a shared reference");
    }

    @Test
    void getCheckpointForNonexistentWatermarkReturnsNull() {
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint();
        mgr.saveCheckpoint(Checkpoint.of("sess", "wm-1", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c", null, "out", 1, 10L));

        assertNull(mgr.getCheckpoint("nonexistent"),
                "getCheckpoint for a nonexistent watermark must return null");
    }

    @Test
    void getLatestForSessionWithNoCheckpointsReturnsNull() {
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint();
        assertNull(mgr.getLatestCheckpoint("never-seen"),
                "getLatestCheckpoint for a session with no checkpoints must return null");
        assertNull(mgr.getLatestCheckpoint(null),
                "getLatestCheckpoint(null) must return null");
    }

    // ========================================================================
    // Per-session isolation
    // ========================================================================

    @Test
    void perSessionCheckpointsAreIsolated() {
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint();

        // Session A saves two checkpoints.
        mgr.saveCheckpoint(Checkpoint.of("sessA", "wm-a0", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c-a0", null, "out-a0", 1, 10L));
        mgr.saveCheckpoint(Checkpoint.of("sessA", "wm-a1", 1, 1001L,
                CheckpointType.TOOL_EXECUTION, "ls", "c-a1", null, "out-a1", 2, 20L));

        // Session B saves one checkpoint.
        mgr.saveCheckpoint(Checkpoint.of("sessB", "wm-b0", 0, 1002L,
                CheckpointType.TOOL_EXECUTION, "pwd", "c-b0", null, "out-b0", 1, 5L));

        // Session A's latest is wm-a1 (not affected by session B).
        Checkpoint latestA = mgr.getLatestCheckpoint("sessA");
        assertEquals("wm-a1", latestA.getWatermark());
        assertEquals("ls", latestA.getToolName());

        // Session B's latest is wm-b0 (not affected by session A).
        Checkpoint latestB = mgr.getLatestCheckpoint("sessB");
        assertEquals("wm-b0", latestB.getWatermark());
        assertEquals("pwd", latestB.getToolName());

        // Watermark index works across sessions.
        assertNotNull(mgr.getCheckpoint("wm-a0"));
        assertNotNull(mgr.getCheckpoint("wm-a1"));
        assertNotNull(mgr.getCheckpoint("wm-b0"));
    }

    // ========================================================================
    // Null-session-id handling (anonymous checkpoints)
    // ========================================================================

    @Test
    void nullSessionIdNotStoredByLatestButIndexedByWatermark() {
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint();

        // A null sessionId checkpoint is indexed by watermark (retrievable via
        // getCheckpoint) but not added to any per-session list (not retrievable
        // via getLatestCheckpoint — there is no session key to look up).
        mgr.saveCheckpoint(Checkpoint.of(null, "wm-anon", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c-anon", null, "out-anon", 1, 10L));

        assertNull(mgr.getLatestCheckpoint(null),
                "getLatestCheckpoint(null) must return null (no per-session list for anonymous)");
        Checkpoint byWm = mgr.getCheckpoint("wm-anon");
        assertNotNull(byWm, "getCheckpoint by watermark must still work for anonymous checkpoints");
        assertEquals("wm-anon", byWm.getWatermark());
    }
}
