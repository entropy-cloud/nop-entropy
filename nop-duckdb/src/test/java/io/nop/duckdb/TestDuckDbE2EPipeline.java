package io.nop.duckdb;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.tablesaw.xlsx.XlsxReadOptions;
import io.nop.tablesaw.xlsx.XlsxReader;
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
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI6 end-to-end pipeline and concurrency (plan 2288 Phase 2). The pipeline runs real task
 * steps from the xlsx entry point (ingested through the WI2 readXlsx bridge API, whose
 * internal temp CSV is transient by contract) through bridge export -> CSV ingest -> filter
 * -> parquet export -> parquet read-back aggregate -> final CSV, plus two write-back faces:
 * SQLite ATTACH direct write (pinned shared instance: a bare JDBC anchor connection keeps
 * the duckdb instance alive across the steps, since ATTACH state does not survive instance
 * shutdown between step connections) and business-table write via the ORM (write boundary:
 * business writes go through ORM only).
 */
public class TestDuckDbE2EPipeline extends BaseTestCase {

    @BeforeAll
    public static void init() {
        setTestConfig("nop.orm.init-database-schema", true);
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static File xlsxResource() throws Exception {
        return new File(TestDuckDbE2EPipeline.class
                .getResource("/io/nop/duckdb/data/columns-with-missing-values.xlsx").toURI());
    }

    /**
     * Runs the full pipeline task on one directory and returns the task outputs together
     * with the golden filtered-row count.
     */
    private long runPipeline(Path dir) throws Exception {
        File xlsx = xlsxResource();
        tech.tablesaw.api.Table expected = new XlsxReader()
                .read(XlsxReadOptions.builder(xlsx.getAbsolutePath()).build());
        String firstCol = expected.columnNames().get(0);
        long xlsxRows = expected.rowCount();

        Path db = dir.resolve("pipeline.duckdb");
        // golden filtered count computed on the same data before the task runs
        long filteredGolden;
        AtomicReference<Map<String, Object>> outRef = new AtomicReference<>();
        // the anchor's config fingerprint must match the bean engine's (@InjectValue resolves
        // the empty @cfg defaults to ""), otherwise the OPEN_FILES registry rejects the file
        DuckDbEngine anchorEngine = new DuckDbEngine();
        anchorEngine.memoryLimit = "";
        anchorEngine.threads = "";
        anchorEngine.tempDirectory = "";
        try (Connection anchor = anchorEngine.openFile(db.toString())) {
            long n = DuckDbFiles.readXlsx(anchor, xlsx.getAbsolutePath(), "t_xlsx");
            assertEquals(xlsxRows, n, "xlsx bridge ingest row count");
            try (Statement st = anchor.createStatement();
                 ResultSet rs = st.executeQuery("SELECT count(*) FROM t_xlsx WHERE \""
                         + firstCol.replace("\"", "\"\"") + "\" IS NOT NULL")) {
                rs.next();
                filteredGolden = rs.getLong(1);
            }

            ITaskFlowManager manager = new TaskFlowManagerImpl();
            ITask task = manager.loadTaskFromPath("/nop/duckdb/task/e2e-pipeline.task.xml");
            ITaskRuntime rt = manager.newTaskRuntime(task, false, null);
            rt.setInput("dbPath", db.toString());
            rt.setInput("bridgeCsv", dir.resolve("bridge.csv").toString());
            rt.setInput("filterSql", "SELECT * FROM t_work WHERE \"" + firstCol.replace("\"", "\"\"")
                    + "\" IS NOT NULL");
            rt.setInput("parquetPath", dir.resolve("out.parquet").toString());
            rt.setInput("finalCsv", dir.resolve("final.csv").toString());
            rt.setInput("sqlitePath", dir.resolve("result.db").toString());

            // the anchor connection stays open across the whole task: step connections are
            // sequential (each a fresh duckdb instance), and ATTACH state lives in the
            // instance catalog only while some connection keeps the instance alive
            TaskStepReturn ret = task.execute(rt);
            if (ret.isAsync()) {
                ret = ret.sync();
            }
            outRef.set(ret.syncGetOutputs());
        } finally {
            anchorEngine.close();
        }

        Map<String, Object> out = outRef.get();
        // sqlite write-back row count is not exported (CTAS updateCount is driver-defined);
        // the write is verified by the independent read-back below
        assertEquals(xlsxRows, ((Number) out.get("bridgeRows")).longValue(), "bridge export rows");
        assertEquals(xlsxRows, ((Number) out.get("ingestedRows")).longValue(), "csv ingest rows");
        assertEquals(filteredGolden, ((Number) out.get("filteredRows")).longValue(),
                "filter step must match the golden count");
        assertEquals(filteredGolden, ((Number) out.get("parquetRows")).longValue(),
                "parquet export must preserve filtered rows");
        assertEquals(1L, ((Number) out.get("aggRows")).longValue(), "aggregate output rows");
        assertEquals(1L, ((Number) out.get("finalRows")).longValue(), "final export rows");

        // final result file content: header + one line carrying the filtered count
        try (BufferedReader r = Files.newBufferedReader(dir.resolve("final.csv"), StandardCharsets.UTF_8)) {
            assertEquals("n", r.readLine().trim(), "final csv header");
            assertEquals(filteredGolden, Long.parseLong(r.readLine().trim()),
                "final csv must carry the filtered golden count");
        }
        return filteredGolden;
    }

    @Test
    @Timeout(300)
    public void testPipelineEndToEndWithSqliteWriteBack() throws Exception {
        Path dir = Files.createTempDirectory("duckdb-wi6-e2e");
        long filteredGolden = runPipeline(dir);

        // independent read-back of the SQLite write-back product: fresh connection,
        // sequential ATTACH + SELECT (single-connection statements, no multi-statement need)
        Path sqliteDb = dir.resolve("result.db");
        assertTrue(Files.exists(sqliteDb), "sqlite write-back file must exist");
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:");
             Statement st = c.createStatement()) {
            st.execute("ATTACH '" + sqliteDb + "' AS sq (TYPE sqlite)");
            try (ResultSet rs = st.executeQuery("SELECT count(*), MAX(n) FROM sq.result")) {
                rs.next();
                assertEquals(1, rs.getInt(1), "sqlite result table rows");
                assertEquals(filteredGolden, rs.getLong(2),
                        "sqlite result value must equal the filtered golden count");
            }
        }
    }

    /**
     * Write boundary: the analysis result lands in the business table through the ORM, and
     * is verified through EQL - business writes never bypass the ORM (roadmap hard rule).
     */
    @Test
    @Timeout(300)
    public void testBusinessWriteBackViaOrm() throws Exception {
        Path dir = Files.createTempDirectory("duckdb-wi6-orm");
        long resultValue = runPipeline(dir);

        IOrmTemplate orm = BeanContainer.getBeanByType(IOrmTemplate.class);
        orm.runInSession(() -> {
            IOrmEntity e = orm.newEntity("duckdb.PipelineResult");
            e.orm_propValueByName("rid", 1L);
            e.orm_propValueByName("cnt", resultValue);
            orm.save(e);
            orm.flushSession();
        });

        QueryBean query = new QueryBean();
        query.setSourceName("duckdb.PipelineResult");
        query.setFieldNames(List.of("rid", "cnt"));
        List<Map<String, Object>> rows = orm.findListByQuery(query);
        assertEquals(1, rows.size(), "business table must hold the pipeline result");
        assertEquals(resultValue, ((Number) rows.get(0).get("cnt")).longValue(), "ORM write-back value");
    }

    /**
     * Concurrency tier: two pipelines on independent .duckdb files run in parallel and both
     * complete with their own goldens; a task step pointed at a cross-process-locked file
     * fails with the WI4 file-locked bizFatal semantics.
     */
    @Test
    @Timeout(600)
    public void testParallelIndependentFilesAndLockConflict() throws Exception {
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int w = 0; w < 2; w++) {
            Thread t = new Thread(() -> {
                try {
                    long golden = runPipeline(Files.createTempDirectory("duckdb-wi6-par"));
                    if (golden < 0) {
                        throw new IllegalStateException("negative golden");
                    }
                } catch (Throwable e) {
                    failure.set(e);
                } finally {
                    done.countDown();
                }
            });
            t.start();
        }
        assertTrue(done.await(300, TimeUnit.SECONDS), "parallel pipelines must finish");
        assertNull(failure.get(), "independent-file parallel tasks must both succeed: "
                + (failure.get() == null ? "" : String.valueOf(failure.get())));

        // lock conflict: task step on a file held by an external process
        Path dir = Files.createTempDirectory("duckdb-wi6-lock");
        Path locked = dir.resolve("locked.duckdb");
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:" + locked);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE init(k INTEGER)");
        }
        Process child = new ProcessBuilder(ProcessHandle.current().info().command().orElse("java"),
                "-cp", System.getProperty("java.class.path"),
                "io.nop.duckdb.LockHolderMain", locked.toString())
                .redirectErrorStream(true).start();
        boolean ready = false;
        try (BufferedReader r = new BufferedReader(
                new java.io.InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
            while (System.currentTimeMillis() < deadline) {
                String line = r.readLine();
                if (line == null) {
                    break;
                }
                if ("READY".equals(line.trim())) {
                    ready = true;
                    break;
                }
            }
        }
        assertTrue(ready, "lock holder must start");
        try {
            ITaskFlowManager manager = new TaskFlowManagerImpl();
            ITask task = manager.loadTaskFromPath("/nop/duckdb/task/locked-file.task.xml");
            ITaskRuntime rt = manager.newTaskRuntime(task, false, null);
            rt.setInput("dbPath", locked.toString());
            NopDuckDbException e = org.junit.jupiter.api.Assertions.assertThrows(
                    NopDuckDbException.class, () -> task.execute(rt));
            assertEquals(NopDuckDbErrors.ERR_DUCKDB_FILE_LOCKED.getErrorCode(), e.getErrorCode(),
                    "cross-process lock must surface as file-locked (WI4 semantics)");
            assertTrue(e.isBizFatal(), "file-locked must not be auto-retried");
        } finally {
            child.destroyForcibly();
            child.waitFor(10, TimeUnit.SECONDS);
        }
    }
}
