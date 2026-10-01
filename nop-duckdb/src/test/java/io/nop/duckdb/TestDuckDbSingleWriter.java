package io.nop.duckdb;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 single-writer semantics: cross-JVM lock conflict maps to the explicit file-locked
 * error (bizFatal, not auto-retried); independent files run in parallel; same-JVM
 * multi-read-single-write is safe.
 */
public class TestDuckDbSingleWriter extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    @Timeout(120)
    public void testCrossJvmLockConflictIsExplicitFileLocked() throws Exception {
        Path dir = Files.createTempDirectory("duckdb-wi4-lock");
        Path db = dir.resolve("locked.duckdb");
        // initialize the file so the child can open it quickly
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:" + db);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE init(k INTEGER)");
        }

        String javaBin = ProcessHandle.current().info().command().orElse("java");
        Process child = new ProcessBuilder(javaBin, "-cp", System.getProperty("java.class.path"),
                "io.nop.duckdb.LockHolderMain", db.toString())
                .redirectErrorStream(true).start();
        boolean ready = false;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
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
            DuckDbEngine engine = new DuckDbEngine();
            NopDuckDbException e = org.junit.jupiter.api.Assertions.assertThrows(NopDuckDbException.class,
                    () -> engine.openFile(db.toString()));
            assertEquals(NopDuckDbErrors.ERR_DUCKDB_FILE_LOCKED.getErrorCode(), e.getErrorCode());
            assertNotNull(e.getCause());
            assertTrue(e.isBizFatal(), "lock conflict must be bizFatal (not auto-retryable)");
            engine.close();
        } finally {
            child.destroyForcibly();
            child.waitFor(10, TimeUnit.SECONDS);
        }

        // after the holder dies the file is writable again
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:" + db);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM init")) {
            assertTrue(rs.next());
        }
    }

    @Test
    @Timeout(60)
    public void testFileLockedErrorIsNotRetried() throws Exception {
        TestFileLockedStep.ATTEMPTS.set(0);
        ITaskFlowManager manager = new TaskFlowManagerImpl();
        ITask task = manager.loadTaskFromPath("/nop/duckdb/task/filelocked-retry.task.xml");
        ITaskRuntime taskRt = manager.newTaskRuntime(task, false, null);

        NopException e = org.junit.jupiter.api.Assertions.assertThrows(NopException.class,
                () -> task.execute(taskRt));
        assertEquals(NopDuckDbErrors.ERR_DUCKDB_FILE_LOCKED.getErrorCode(),
                ((NopDuckDbException) e).getErrorCode());
        assertEquals(1, TestFileLockedStep.ATTEMPTS.get(),
                "bizFatal file-locked must not be retried despite <retry> config");
    }

    @Test
    @Timeout(120)
    public void testParallelIndependentFiles() throws Exception {
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int w = 0; w < 2; w++) {
            final int worker = w;
            Thread t = new Thread(() -> {
                try {
                    Path db = Files.createTempDirectory("duckdb-wi4-par").resolve("w" + worker + ".duckdb");
                    DuckDbEngine engine = new DuckDbEngine();
                    try (Connection conn = engine.openFile(db.toString());
                         Statement st = conn.createStatement()) {
                        st.execute("CREATE TABLE data(w INTEGER, k INTEGER)");
                        for (int i = 0; i < 50; i++) {
                            st.execute("INSERT INTO data VALUES (" + worker + "," + i + ")");
                        }
                        ResultSet rs = st.executeQuery(
                                "SELECT count(*) FROM data WHERE w=" + worker);
                        rs.next();
                        assertEquals(50, rs.getInt(1));
                    } finally {
                        engine.close();
                    }
                } catch (Throwable e) {
                    failure.set(e);
                } finally {
                    done.countDown();
                }
            });
            t.start();
        }
        assertTrue(done.await(60, TimeUnit.SECONDS), "parallel workers must finish");
        assertTrue(failure.get() == null, "independent files must run in parallel without conflict: "
                + failure.get());
    }

    @Test
    @Timeout(60)
    public void testSameJvmMultiReadSingleWrite() throws Exception {
        Path db = Files.createTempDirectory("duckdb-wi4-mr").resolve("shared.duckdb");
        DuckDbEngine engine = new DuckDbEngine();
        try {
            try (Connection w = engine.openFile(db.toString());
                 Statement ws = w.createStatement()) {
                ws.execute("CREATE TABLE t(k INTEGER)");
                ws.execute("INSERT INTO t VALUES (1),(2),(3)");

                // two concurrent readers on the same file (same JVM shared instance) while writing
                CountDownLatch readersDone = new CountDownLatch(2);
                AtomicReference<Throwable> readerFailure = new AtomicReference<>();
                for (int r = 0; r < 2; r++) {
                    new Thread(() -> {
                        try (Connection rd = engine.openFile(db.toString());
                             Statement st = rd.createStatement();
                             ResultSet rs = st.executeQuery("SELECT count(*) FROM t")) {
                            rs.next();
                            rs.getInt(1);
                        } catch (Throwable e) {
                            readerFailure.set(e);
                        } finally {
                            readersDone.countDown();
                        }
                    }).start();
                }
                ws.execute("INSERT INTO t VALUES (4)");
                assertTrue(readersDone.await(30, TimeUnit.SECONDS));
                assertTrue(readerFailure.get() == null,
                        "same-JVM readers must not conflict: " + readerFailure.get());
            }
        } finally {
            engine.close();
        }
    }
}
