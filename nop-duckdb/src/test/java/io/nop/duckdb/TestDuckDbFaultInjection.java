package io.nop.duckdb;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.impl.TaskFlowManagerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 fault injection: native library missing surfaces at the step boundary as the explicit
 * native-load-failed error (never a bare Error); an unwritable temp_directory with a forced
 * out-of-core workload surfaces as an explicit duckdb-managed step failure (io-failed), and
 * the failed step leaves no partial export file.
 */
public class TestDuckDbFaultInjection extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    @Timeout(60)
    public void testNativeMissingSurfacesAsExplicitErrorAtStepLevel() {
        ITaskFlowManager manager = new TaskFlowManagerImpl();
        ITask task = manager.loadTaskFromPath("/nop/duckdb/task/broken-native.task.xml");
        ITaskRuntime taskRt = manager.newTaskRuntime(task, false, null);

        NopDuckDbException e = assertThrows(NopDuckDbException.class,
                () -> task.execute(taskRt));
        assertEquals(NopDuckDbErrors.ERR_DUCKDB_NATIVE_LOAD_FAILED.getErrorCode(), e.getErrorCode());
        assertNotNull(e.getCause(), "cause chain preserved");
        assertTrue(e.isBizFatal(), "native load failure is permanent");
    }

    /**
     * Recorded semantics (WI0 Q2 + probe-tuned volume, plan 2286): with a tight 64MB budget
     * and an unwritable temp_directory, an out-of-core sort over ~180MB of narrow rows must
     * fail as an explicit duckdb-managed step error (io-failed; invalid-config if the SET
     * itself is rejected) - never a silent wrong result and never a JVM OOM. The exact
     * message shape is deliberately not asserted (WI4 plan adjudication).
     */
    @Test
    @Timeout(300)
    public void testUnwritableTempDirSurfacesExplicitFailure() throws Exception {
        Path dir = Files.createTempDirectory("duckdb-wi4-fault");
        Path csv = dir.resolve("big.csv");
        // 20M narrow rows (~180MB) vs 64MB budget forces out-of-core execution; narrow rows
        // keep the streaming CSV scanner inside its bounded buffers (wide-string scan is the
        // OOM-prone shape recorded by WI0)
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            w.println("k");
            for (int i = 0; i < 20_000_000; i++) {
                w.println(i);
            }
        }
        Path export = dir.resolve("out.csv");

        ITaskFlowManager manager = new TaskFlowManagerImpl();
        ITask task = manager.loadTaskFromPath("/nop/duckdb/task/fault-spill.task.xml");
        ITaskRuntime taskRt = manager.newTaskRuntime(task, false, null);
        taskRt.setInput("csvPath", csv.toString());
        taskRt.setInput("exportPath", export.toString());

        NopDuckDbException e = assertThrows(NopDuckDbException.class,
                () -> task.execute(taskRt));
        assertTrue(e.getErrorCode().equals(NopDuckDbErrors.ERR_DUCKDB_IO_FAILED.getErrorCode())
                        || e.getErrorCode().equals(NopDuckDbErrors.ERR_DUCKDB_INVALID_CONFIG.getErrorCode()),
                "explicit duckdb-managed error expected, got: " + e.getErrorCode() + " / " + e.getMessage());
        assertFalse(Files.exists(export), "failed step must not leave a partial export file");
        System.out.println("[WI4-FI] unwritable temp failure shape: " + e.getErrorCode()
                + " / " + e.getMessage());
    }

    /**
     * Control: the same workload shape through the default engine (no tight budget, no
     * unusable spill location) completes and exports - the fault test's failure therefore
     * comes from the budget/spill configuration, not from the workload or the step wiring.
     * (A tight 64MB budget is itself too small for even small workloads - DuckDB needs
     * >=30MB contiguous blocks - so the control must run on the default engine.)
     */
    @Test
    @Timeout(300)
    public void testSpillWorkloadCompletesWithDefaultEngine() throws Exception {
        Path dir = Files.createTempDirectory("duckdb-wi4-fault-control");
        Path csv = dir.resolve("small.csv");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            w.println("k");
            for (int i = 0; i < 1_000_000; i++) {
                w.println(i);
            }
        }
        Path export = dir.resolve("out.csv");

        ITaskFlowManager manager = new TaskFlowManagerImpl();
        ITask task = manager.loadTaskFromPath("/nop/duckdb/task/fault-spill-control.task.xml");
        ITaskRuntime taskRt = manager.newTaskRuntime(task, false, null);
        taskRt.setInput("csvPath", csv.toString());
        taskRt.setInput("exportPath", export.toString());

        assertDoesNotThrow(() -> {
            task.execute(taskRt);
        });
        assertTrue(Files.exists(export), "control workload must export successfully");
    }
}
