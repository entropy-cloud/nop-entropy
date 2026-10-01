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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 orchestration tests: the DuckDbSqlTaskStep bean is driven through real task.xml
 * definitions via ITaskFlowManager (wiring verification), covering the ingest->sql->export
 * pipeline, retry decorator compatibility, parameterized binding and input validation.
 */
public class TestDuckDbTaskStep extends BaseTestCase {

    private static ITaskFlowManager taskFlowManager;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        taskFlowManager = new TaskFlowManagerImpl();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private Path writeCsv(String content) throws Exception {
        Path csv = Files.createTempDirectory("duckdb-wi3").resolve("in.csv");
        Files.writeString(csv, content, StandardCharsets.UTF_8);
        return csv;
    }

    @Test
    @Timeout(120)
    public void testPipelineIngestSqlExport() throws Exception {
        Path csv = writeCsv("k,name\n1,alice\n6,bob\n7,carol\n2,dave\n");
        Path dbFile = Files.createTempDirectory("duckdb-wi3-db").resolve("pipe.duckdb");
        Path export = Files.createTempDirectory("duckdb-wi3-out").resolve("out.parquet");

        ITask task = taskFlowManager.loadTaskFromPath("/nop/duckdb/task/pipeline.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("dbPath", dbFile.toString());
        taskRt.setInput("csvPath", csv.toString());
        taskRt.setInput("threshold", 5);
        taskRt.setInput("filteredTable", "t_filtered");
        taskRt.setInput("exportPath", export.toString());

        Map<String, Object> outputs = task.execute(taskRt).syncGetOutputs();
        assertEquals(4, ((Number) outputs.get("ingestedRows")).intValue());
        assertEquals(2, ((Number) outputs.get("filteredRows")).intValue());
        assertEquals(2, ((Number) outputs.get("exportedRows")).intValue());
        assertEquals("t_filtered", outputs.get("filteredTableName"),
                "table name must flow from the filter step's output to the export step via scope");
        assertTrue(Files.exists(export), "export parquet must be produced");

        // export content matches the filtered rows (read back via plain engine)
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:duckdb:" + dbFile, "sa", "");
             java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT count(*), sum(k) FROM read_parquet('" + export + "')")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
            assertEquals(13L, rs.getLong(2));
        }
    }

    @Test
    @Timeout(120)
    public void testRetryDecoratorFirstAttemptFailsThenSucceeds() throws Exception {
        TestFlakyDuckDbStep.ATTEMPTS.set(0);
        ITask task = taskFlowManager.loadTaskFromPath("/nop/duckdb/task/retry.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);

        Map<String, Object> outputs = task.execute(taskRt).syncGetOutputs();
        assertEquals(1, ((Number) outputs.get("rowCount")).intValue());
        assertTrue(TestFlakyDuckDbStep.ATTEMPTS.get() >= 2,
                "retry decorator must re-execute the step after first failure, attempts="
                        + TestFlakyDuckDbStep.ATTEMPTS.get());
    }

    @Test
    @Timeout(60)
    public void testParameterizedBindingResistsInjection() throws Exception {
        Path dbFile = Files.createTempDirectory("duckdb-wi3-bind").resolve("bind.duckdb");
        DuckDbEngine engine = new DuckDbEngine();
        try (java.sql.Connection conn = engine.openFile(dbFile.toString());
             java.sql.Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE t(name VARCHAR)");
            st.execute("INSERT INTO t VALUES ('real')");
        } finally {
            engine.close();
        }

        ITask task = taskFlowManager.loadTaskFromPath("/nop/duckdb/task/params.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        taskRt.setInput("dbPath", dbFile.toString());
        taskRt.setInput("evil", "x'; DROP TABLE t;--");

        Map<String, Object> outputs = task.execute(taskRt).syncGetOutputs();
        assertEquals(0, ((Number) outputs.get("evilMatches")).intValue(),
                "injected value must be bound as a literal, matching nothing");

        // target table must be intact
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:duckdb:" + dbFile, "sa", "");
             java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT count(*) FROM t")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "DROP injection must not take effect");
        }
    }

    @Test
    @Timeout(60)
    public void testMissingSqlFailsFast() {
        NopDuckDbException e = org.junit.jupiter.api.Assertions.assertThrows(NopDuckDbException.class, () -> {
            ITask task = taskFlowManager.loadTaskFromPath("/nop/duckdb/task/invalid-missing-sql.task.xml");
            ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
            task.execute(taskRt);
        });
        assertEquals(NopDuckDbErrors.ERR_DUCKDB_INVALID_STEP_INPUT.getErrorCode(), e.getErrorCode());
    }
}
