package io.nop.duckdb;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.impl.TaskFlowManagerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI6 deep-spill scenario (plan 2288 Phase 1): a 20M-row / ~160MB workload under a 128MB
 * budget must complete the full task chain (ingest -> aggregate -> full sort -> export)
 * out-of-core without OOM and without silent truncation. The spill itself is observed by
 * polling the engine's spill directory while the task runs on a worker thread (DuckDB
 * deletes temp files as blocks are released, so post-hoc checks would be false negatives).
 */
public class TestDuckDbDeepSpill extends BaseTestCase {
    static final int ROWS = 20_000_000;
    static final int GROUPS = 1000;
    static final int K_MOD = 977;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static void writeBigCsv(Path csv) throws Exception {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            w.println("g,k");
            for (int i = 0; i < ROWS; i++) {
                w.println((i % GROUPS) + "," + (i % K_MOD));
            }
        }
    }

    private static void writeDimCsv(Path csv) throws Exception {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            w.println("g,tag");
            for (int i = 0; i < GROUPS; i++) {
                w.println(i + ",tag" + i);
            }
        }
    }

    @Test
    @Timeout(600)
    public void testDeepSpillChainCompletesOutOfCore() throws Exception {
        Path dir = Files.createTempDirectory("duckdb-wi6-deep");
        Path csv = dir.resolve("big.csv");
        Path dim = dir.resolve("dim.csv");
        Path export = dir.resolve("sorted.csv");
        Path db = dir.resolve("work.duckdb");
        writeBigCsv(csv);
        writeDimCsv(dim);

        TestSpillTaskEngine engine =
                (TestSpillTaskEngine) BeanContainer.instance().getBean("testSpillTaskEngine");
        AtomicBoolean spillSeen = new AtomicBoolean(false);
        AtomicReference<Map<String, Object>> outputs = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try {
                ITaskFlowManager manager = new TaskFlowManagerImpl();
                ITask task = manager.loadTaskFromPath("/nop/duckdb/task/deep-spill.task.xml");
                ITaskRuntime rt = manager.newTaskRuntime(task, false, null);
                rt.setInput("dbPath", db.toString());
                rt.setInput("csvPath", csv.toString());
                rt.setInput("dimCsvPath", dim.toString());
                rt.setInput("exportPath", export.toString());
                TaskStepReturn ret = task.execute(rt);
                if (ret.isAsync()) {
                    ret = ret.sync();
                }
                outputs.set(ret.syncGetOutputs());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        worker.start();
        // poll the spill directory while the task runs; DuckDB removes temp files as their
        // blocks are released, so observing any entry during execution proves out-of-core
        while (worker.isAlive()) {
            try (var list = Files.list(engine.getSpillDirectory())) {
                if (list.findAny().isPresent()) {
                    spillSeen.set(true);
                }
            }
            worker.join(200);
        }
        worker.join();

        assertNull(failure.get(), "deep-spill task chain must complete: "
                + (failure.get() == null ? "" : String.valueOf(failure.get())));
        assertTrue(spillSeen.get(), "task must have spilled to the temp directory (out-of-core)");

        Map<String, Object> out = outputs.get();
        assertEquals(GROUPS, ((Number) out.get("aggRows")).intValue(), "aggregate group count");
        assertEquals(GROUPS, ((Number) out.get("joinedRows")).intValue(), "join row count");
        assertEquals((long) ROWS, ((Number) out.get("exportedRows")).longValue(),
                "export must not silently truncate the sorted 20M-row result");

        // sorted desc: the first data row must carry the max k value
        try (BufferedReader r = Files.newBufferedReader(export, StandardCharsets.UTF_8)) {
            String header = r.readLine();
            assertEquals("g,k", header, "export header");
            String first = r.readLine();
            assertTrue(first.endsWith("," + (K_MOD - 1)),
                    "first sorted row must have max k=" + (K_MOD - 1) + ", got: " + first);
        }
    }

    /**
     * Handle-leak proxy: 50 open/query/close cycles all succeed, and after engine.close()
     * a bare DriverManager connection can immediately reopen the file (all tracked
     * connections released, no lock residue). Bare DriverManager avoids the
     * config-fingerprint registry (a second engine with a different config would be
     * rejected as config-conflict by design, WI4).
     */
    @Test
    @Timeout(300)
    public void testConnectionCyclesReleaseLocks() throws Exception {
        Path db = Files.createTempDirectory("duckdb-wi6-cycle").resolve("cycle.duckdb");
        DuckDbEngine engine = new DuckDbEngine();
        try {
            for (int i = 0; i < 50; i++) {
                try (Connection c = engine.openFile(db.toString());
                     Statement st = c.createStatement()) {
                    st.execute("CREATE TABLE IF NOT EXISTS t(k INTEGER)");
                    try (ResultSet rs = st.executeQuery("SELECT count(*) FROM t")) {
                        rs.next();
                    }
                }
            }
        } finally {
            engine.close();
        }
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:" + db);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM t")) {
            rs.next();
            assertEquals(0, rs.getInt(1), "file must be readable after all connections released");
        }
    }
}
