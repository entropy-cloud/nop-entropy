package io.nop.ai.agent.reliability;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 unit + serialization tests for the checkpoint idempotency_key
 * (design §13.2): verifies deterministic key computation, type-specific
 * behavior (Decision F), the 11-param factory compatibility (Decision D),
 * and full serialization round-trip fidelity for the journal + DB backends
 * (Decision E).
 */
public class TestCheckpointIdempotencyKey {

    @TempDir
    Path tempDir;

    private DataSource dataSource;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:test-idempotency-key-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
    }

    @AfterEach
    void tearDown() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort close
            }
        }
    }

    // ========================================================================
    // Key computation (Decision C / F)
    // ========================================================================

    @Test
    void toolExecutionKeyIsNotNullAndDeterministic() {
        Checkpoint a = Checkpoint.of("sess", "wm-a", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-1", "ls -la", "out", 1, 10L);
        Checkpoint b = Checkpoint.of("sess", "wm-b", 1, 1001L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-1", "ls -la", "other-out", 2, 20L);

        assertNotNull(a.getIdempotencyKey(),
                "TOOL_EXECUTION checkpoint must have a non-null idempotency key");
        assertEquals(a.getIdempotencyKey(), b.getIdempotencyKey(),
                "Same toolName+callId+inputSummary must yield the same key (deterministic), "
                        + "regardless of watermark/output/messageCount");
        assertEquals(32, a.getIdempotencyKey().length(),
                "Key must be 32 hex chars (128 bits)");
    }

    @Test
    void differentToolInputsProduceDifferentKeys() {
        Checkpoint base = Checkpoint.of("sess", "wm-1", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-1", "input-A", "out", 1, 10L);

        assertNotEquals(base.getIdempotencyKey(),
                Checkpoint.of("sess", "wm-2", 0, 1000L,
                        CheckpointType.TOOL_EXECUTION, "echo", "call-1", "input-B", "out", 1, 10L)
                        .getIdempotencyKey(),
                "Different inputSummary must produce a different key");
        assertNotEquals(base.getIdempotencyKey(),
                Checkpoint.of("sess", "wm-3", 0, 1000L,
                        CheckpointType.TOOL_EXECUTION, "ls", "call-1", "input-A", "out", 1, 10L)
                        .getIdempotencyKey(),
                "Different toolName must produce a different key");
        assertNotEquals(base.getIdempotencyKey(),
                Checkpoint.of("sess", "wm-4", 0, 1000L,
                        CheckpointType.TOOL_EXECUTION, "echo", "call-2", "input-A", "out", 1, 10L)
                        .getIdempotencyKey(),
                "Different callId must produce a different key");
    }

    @Test
    void nullComponentsMapToStableKey() {
        // null inputSummary on a TOOL_EXECUTION must still yield a stable,
        // non-null key (null treated as empty string) — not throw.
        Checkpoint a = Checkpoint.of("sess", "wm-a", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-1", null, "out", 1, 10L);
        Checkpoint b = Checkpoint.of("sess", "wm-b", 1, 1001L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-1", null, "out2", 2, 20L);
        assertNotNull(a.getIdempotencyKey());
        assertEquals(a.getIdempotencyKey(), b.getIdempotencyKey(),
                "Both-null inputSummary must yield the same stable key");
    }

    @Test
    void nonToolExecutionTypesHaveNullKey() {
        Checkpoint llm = Checkpoint.of("sess", "wm-llm", 0, 1000L,
                CheckpointType.LLM_TURN, null, null, null, "llm-out", 1, 10L);
        Checkpoint compaction = Checkpoint.of("sess", "wm-comp", 1, 1001L,
                CheckpointType.COMPACTION, null, null, null, "comp-out", 2, 20L);

        assertNull(llm.getIdempotencyKey(),
                "LLM_TURN key must be null (Decision F: not a tool-call divergence point)");
        assertNull(compaction.getIdempotencyKey(),
                "COMPACTION key must be null (Decision F)");
    }

    // ========================================================================
    // Factory compatibility (Decision D)
    // ========================================================================

    @Test
    void elevenParamFactoryAutoComputesKeyMatchingComputeIdempotencyKey() {
        String expected = Checkpoint.computeIdempotencyKey(
                CheckpointType.TOOL_EXECUTION, "echo", "call-1", "input");
        Checkpoint cp = Checkpoint.of("sess", "wm-1", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-1", "input", "out", 1, 10L);
        assertEquals(expected, cp.getIdempotencyKey(),
                "11-param of() must auto-compute the same key as computeIdempotencyKey");
    }

    @Test
    void twelveParamFactoryPassesExplicitKeyVerbatim() {
        // The deserialization path passes a stored key through verbatim — even
        // a key that would NOT match a recompute. This is what lets restore
        // detect divergence (a tampered/different stored key is preserved, not
        // silently recomputed).
        Checkpoint cp = Checkpoint.of("sess", "wm-1", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-1", "input", "out", 1, 10L,
                "explicit-stored-key");
        assertEquals("explicit-stored-key", cp.getIdempotencyKey(),
                "12-param of() must pass the explicit key through verbatim");
    }

    @Test
    void twelveParamFactoryAcceptsNullKeyForLegacyData() {
        Checkpoint cp = Checkpoint.of("sess", "wm-1", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-1", "input", "out", 1, 10L, null);
        assertNull(cp.getIdempotencyKey(),
                "12-param of() must accept null key (legacy data / non-TOOL_EXECUTION)");
    }

    // ========================================================================
    // Journal serialization round-trip (Decision E)
    // ========================================================================

    @Test
    void journalRoundTripPreservesIdempotencyKey() {
        Path journalFile = tempDir.resolve("sess-j/journal.md");
        CheckpointJournalWriter writer = new CheckpointJournalWriter();
        CheckpointJournalReader reader = new CheckpointJournalReader();

        Checkpoint original = Checkpoint.of("sess-j", "wm-j-1", 0, 1718445600123L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-j", "input-j", "out-j", 5, 99L);
        assertNotNull(original.getIdempotencyKey());

        writer.appendCheckpoint(journalFile, "sess-j", original);
        List<Checkpoint> read = reader.readAll(journalFile);

        assertEquals(1, read.size());
        assertEquals(original.getIdempotencyKey(), read.get(0).getIdempotencyKey(),
                "Journal round-trip must preserve the idempotency key");
        assertEquals(original, read.get(0), "Full equality must hold after round-trip");
    }

    @Test
    void legacyJournalSectionWithoutKeyLineReadsBackNullKey() throws Exception {
        // A journal section written before §13.2 has no idempotencyKey line.
        // It must parse successfully and yield a null key (best-effort fallback).
        Path journalFile = tempDir.resolve("sess-leg/journal.md");
        String legacySection = ""
                + "# Checkpoint Journal - sess-leg\n\n"
                + "## CP-000\n"
                + "type: TOOL_EXECUTION\n"
                + "seq: 0\n"
                + "timestamp: " + java.time.Instant.ofEpochMilli(1000L).toString() + "\n"
                + "sessionId: \"sess-leg\"\n"
                + "watermark: \"wm-leg\"\n"
                + "toolName: \"echo\"\n"
                + "callId: \"call-leg\"\n"
                + "inputSummary: \"in\"\n"
                + "outputSummary: \"out\"\n"
                + "messageCount: 1\n"
                + "tokenEstimate: 10\n\n";
        java.nio.file.Files.createDirectories(journalFile.getParent());
        java.nio.file.Files.write(journalFile, legacySection.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);

        CheckpointJournalReader reader = new CheckpointJournalReader();
        List<Checkpoint> read = reader.readAll(journalFile);
        assertEquals(1, read.size());
        assertNull(read.get(0).getIdempotencyKey(),
                "Legacy journal section (no idempotencyKey line) must read back a null key");
        assertEquals("wm-leg", read.get(0).getWatermark(),
                "Legacy section must still parse all other fields correctly");
    }

    // ========================================================================
    // DB serialization round-trip (Decision E)
    // ========================================================================

    @Test
    void dbRoundTripPreservesIdempotencyKey() {
        Checkpoint original = Checkpoint.of("sess-db", "wm-db-1", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-db", "input-db", "out-db", 5, 99L);
        assertNotNull(original.getIdempotencyKey());

        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);
        mgr1.saveCheckpoint(original);

        // New instance — forces DB reload, proving the key survives persistence.
        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        Checkpoint loaded = mgr2.getCheckpoint("wm-db-1");
        assertNotNull(loaded);
        assertEquals(original.getIdempotencyKey(), loaded.getIdempotencyKey(),
                "DB round-trip must preserve the idempotency key");
        assertEquals(original, loaded, "Full equality must hold after DB round-trip");
    }

    @Test
    void dbRoundTripPreservesNullKeyForNonToolExecution() {
        Checkpoint llm = Checkpoint.of("sess-db-llm", "wm-db-llm", 0, 1000L,
                CheckpointType.LLM_TURN, null, null, null, "llm-out", 5, 99L);
        assertNull(llm.getIdempotencyKey());

        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);
        mgr1.saveCheckpoint(llm);

        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        Checkpoint loaded = mgr2.getCheckpoint("wm-db-llm");
        assertNotNull(loaded);
        assertNull(loaded.getIdempotencyKey(),
                "DB round-trip must preserve null key for non-TOOL_EXECUTION (Decision F)");
    }

    @Test
    void uniqueIndexRejectsDuplicateIdempotencyKey() {
        // Decision G: two distinct watermarks with the SAME tool-call
        // fingerprint (same toolName+callId+inputSummary) must be rejected by
        // the unique index (idempotency dedup). Different watermarks but the
        // same fingerprint = the same logical tool call recorded twice.
        DBCheckpointManager mgr = new DBCheckpointManager(dataSource);
        mgr.saveCheckpoint(Checkpoint.of("sess-uq", "wm-uq-1", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-dup", "input", "out", 1, 10L));

        // A second checkpoint with the same (toolName, callId, inputSummary)
        // but a different watermark — same idempotency key → unique violation.
        Checkpoint dup = Checkpoint.of("sess-uq", "wm-uq-2", 1, 1001L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-dup", "input", "out2", 2, 20L);
        assertEquals(mgr.getCheckpoint("wm-uq-1").getIdempotencyKey(),
                dup.getIdempotencyKey(),
                "Sanity: same fingerprint yields the same key");

        io.nop.ai.agent.engine.NopAiAgentException ex = org.junit.jupiter.api.Assertions.assertThrows(
                io.nop.ai.agent.engine.NopAiAgentException.class,
                () -> mgr.saveCheckpoint(dup),
                "Duplicate idempotency key must be rejected by the unique index");
        assertTrue(ex.getMessage().contains("wm-uq-2"),
                "Exception must name the rejected checkpoint watermark");
    }
}
