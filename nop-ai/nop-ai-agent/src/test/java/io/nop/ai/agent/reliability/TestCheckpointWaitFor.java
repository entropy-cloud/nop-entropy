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
 * Phase 2 unit + serialization tests for the checkpoint {@code wait_for}
 * condition JSON (design §13.1): verifies WAIT_FOR type construction, the
 * {@code wait_for} field round-trip fidelity for the journal + DB backends
 * (Decision F), factory compatibility (Decision A), and legacy backward
 * compatibility (Decision F — null fallback).
 */
public class TestCheckpointWaitFor {

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
        ds.setUrl("jdbc:h2:mem:test-wait-for-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
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
                // dataSource close failure during test teardown is not actionable
            }
        }
    }

    private static final String WAIT_CONDITION_JSON =
            "{\"type\":\"event\",\"key\":\"user-approval\"}";

    // ========================================================================
    // Construction + factory (Decision A)
    // ========================================================================

    @Test
    void waitForCheckpointHoldsConditionJson() {
        Checkpoint cp = Checkpoint.of("sess", "wm-wait", 0, 1000L,
                CheckpointType.WAIT_FOR, null, null, null, null, 1, 10L, null,
                WAIT_CONDITION_JSON);
        assertEquals(WAIT_CONDITION_JSON, cp.getWaitFor(),
                "WAIT_FOR checkpoint must hold the condition JSON");
        assertEquals(CheckpointType.WAIT_FOR, cp.getType());
    }

    @Test
    void waitForTypeHasNullIdempotencyKey() {
        Checkpoint cp = Checkpoint.of("sess", "wm-w", 0, 1000L,
                CheckpointType.WAIT_FOR, null, null, null, null, 1, 10L);
        assertNull(cp.getIdempotencyKey(),
                "WAIT_FOR idempotencyKey must be null (Decision A/F: not a tool-call divergence point)");
    }

    @Test
    void oldFactoriesProduceNullWaitFor() {
        Checkpoint cp11 = Checkpoint.of("sess", "wm-11", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-1", "in", "out", 1, 10L);
        assertNull(cp11.getWaitFor(),
                "11-param of() must produce null waitFor (backward compat)");

        Checkpoint cp12 = Checkpoint.of("sess", "wm-12", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-1", "in", "out", 1, 10L,
                "explicit-key");
        assertNull(cp12.getWaitFor(),
                "12-param of() must produce null waitFor (backward compat)");
    }

    @Test
    void fourteenParamFactoryAcceptsNullWaitFor() {
        Checkpoint cp = Checkpoint.of("sess", "wm-n", 0, 1000L,
                CheckpointType.WAIT_FOR, null, null, null, null, 1, 10L, null, null);
        assertNull(cp.getWaitFor(),
                "14-param of() must accept null waitFor");
    }

    // ========================================================================
    // equals / hashCode / toString (Decision F)
    // ========================================================================

    @Test
    void equalsAndHashCodeIncludeWaitFor() {
        Checkpoint a = Checkpoint.of("sess", "wm-e", 0, 1000L,
                CheckpointType.WAIT_FOR, null, null, null, null, 1, 10L, null,
                WAIT_CONDITION_JSON);
        Checkpoint b = Checkpoint.of("sess", "wm-e", 0, 1000L,
                CheckpointType.WAIT_FOR, null, null, null, null, 1, 10L, null,
                WAIT_CONDITION_JSON);
        Checkpoint c = Checkpoint.of("sess", "wm-e", 0, 1000L,
                CheckpointType.WAIT_FOR, null, null, null, null, 1, 10L, null,
                "{\"type\":\"timeout\",\"deadlineMs\":9999}");

        assertEquals(a, b, "Same waitFor must be equal");
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c, "Different waitFor must not be equal");
    }

    @Test
    void toStringIncludesWaitFor() {
        Checkpoint cp = Checkpoint.of("sess", "wm-t", 0, 1000L,
                CheckpointType.WAIT_FOR, null, null, null, null, 1, 10L, null,
                WAIT_CONDITION_JSON);
        assertTrue(cp.toString().contains("waitFor="),
                "toString must include waitFor field");
    }

    // ========================================================================
    // Journal serialization round-trip (Decision F)
    // ========================================================================

    @Test
    void journalRoundTripPreservesWaitFor() {
        Path journalFile = tempDir.resolve("sess-jw/journal.md");
        CheckpointJournalWriter writer = new CheckpointJournalWriter();
        CheckpointJournalReader reader = new CheckpointJournalReader();

        Checkpoint original = Checkpoint.of("sess-jw", "wm-jw-1", 0, 1718445600123L,
                CheckpointType.WAIT_FOR, null, null, null, null, 5, 99L, null,
                WAIT_CONDITION_JSON);

        writer.appendCheckpoint(journalFile, "sess-jw", original);
        List<Checkpoint> read = reader.readAll(journalFile);

        assertEquals(1, read.size());
        assertEquals(WAIT_CONDITION_JSON, read.get(0).getWaitFor(),
                "Journal round-trip must preserve the wait_for condition JSON");
        assertEquals(original, read.get(0), "Full equality must hold after round-trip");
    }

    @Test
    void legacyJournalSectionWithoutWaitForLineReadsBackNull() throws Exception {
        Path journalFile = tempDir.resolve("sess-leg2/journal.md");
        String legacySection = ""
                + "# Checkpoint Journal - sess-leg2\n\n"
                + "## CP-000\n"
                + "type: TOOL_EXECUTION\n"
                + "seq: 0\n"
                + "timestamp: " + java.time.Instant.ofEpochMilli(1000L).toString() + "\n"
                + "sessionId: \"sess-leg2\"\n"
                + "watermark: \"wm-leg2\"\n"
                + "toolName: \"echo\"\n"
                + "callId: \"call-leg2\"\n"
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
        assertNull(read.get(0).getWaitFor(),
                "Legacy journal section (no waitFor line) must read back a null waitFor");
    }

    // ========================================================================
    // DB serialization round-trip (Decision F)
    // ========================================================================

    @Test
    void dbRoundTripPreservesWaitFor() {
        Checkpoint original = Checkpoint.of("sess-dbw", "wm-dbw-1", 0, 1000L,
                CheckpointType.WAIT_FOR, null, null, null, null, 5, 99L, null,
                WAIT_CONDITION_JSON);

        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);
        mgr1.saveCheckpoint(original);

        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        Checkpoint loaded = mgr2.getCheckpoint("wm-dbw-1");
        assertNotNull(loaded);
        assertEquals(WAIT_CONDITION_JSON, loaded.getWaitFor(),
                "DB round-trip must preserve the wait_for condition JSON");
        assertEquals(original, loaded, "Full equality must hold after DB round-trip");
    }

    @Test
    void dbRoundTripPreservesNullWaitForForNonWaitForType() {
        Checkpoint tool = Checkpoint.of("sess-dbt", "wm-dbt-1", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-dbt", "input", "out", 5, 99L);
        assertNull(tool.getWaitFor());

        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);
        mgr1.saveCheckpoint(tool);

        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        Checkpoint loaded = mgr2.getCheckpoint("wm-dbt-1");
        assertNotNull(loaded);
        assertNull(loaded.getWaitFor(),
                "DB round-trip must preserve null waitFor for non-WAIT_FOR type");
    }
}
