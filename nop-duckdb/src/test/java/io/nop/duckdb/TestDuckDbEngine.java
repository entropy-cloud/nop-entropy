package io.nop.duckdb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for DuckDbEngine: lifecycle, config SET application, config-conflict paths,
 * native load wrapping, and the engine-level end-to-end file working-set path.
 */
public class TestDuckDbEngine {

    private final DuckDbEngine engine = new DuckDbEngine();

    @AfterEach
    public void tearDown() {
        engine.close();
    }

    private DuckDbEngine newEngine(String memoryLimit) {
        DuckDbEngine e = new DuckDbEngine();
        e.memoryLimit = memoryLimit;
        return e;
    }

    @Test
    @Timeout(60)
    public void testOpenMemoryAndQuery() throws Exception {
        try (Connection conn = engine.openMemory();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 42")) {
            assertTrue(rs.next());
            assertEquals(42, rs.getInt(1));
        }
    }

    @Test
    @Timeout(60)
    public void testMemoryLimitSetApplied() throws Exception {
        engine.memoryLimit = "128MB";
        try (Connection conn = engine.openMemory();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT current_setting('memory_limit')")) {
            assertTrue(rs.next());
            String limit = rs.getString(1);
            // DuckDB normalizes the requested value (WI0: 256MB -> 244.1 MiB); assert applied, not literal
            assertTrue(limit.contains("MiB"), "memory_limit should be applied and normalized, got " + limit);
        }
    }

    @Test
    @Timeout(60)
    public void testInvalidThreadsConfigFailsFast() {
        engine.threads = "not-a-number";
        NopDuckDbException e = assertThrows(NopDuckDbException.class, engine::openMemory);
        assertEquals(NopDuckDbErrors.ERR_DUCKDB_INVALID_CONFIG.getErrorCode(), e.getErrorCode());
        assertTrue(e.getParams().get(NopDuckDbErrors.ARG_CONFIG_KEY).equals("threads"));
    }

    @Test
    @Timeout(60)
    public void testInvalidMemoryLimitValueFailsFastFromSet() {
        engine.memoryLimit = "definitely-not-a-size";
        NopDuckDbException e = assertThrows(NopDuckDbException.class, engine::openMemory);
        assertEquals(NopDuckDbErrors.ERR_DUCKDB_INVALID_CONFIG.getErrorCode(), e.getErrorCode());
        assertEquals("memory_limit", e.getParams().get(NopDuckDbErrors.ARG_CONFIG_KEY));
    }

    @Test
    @Timeout(60)
    public void testEngineCloseReleasesAndRejectsFurtherUse() throws Exception {
        Connection conn = engine.openMemory();
        Statement st = conn.createStatement();
        st.execute("CREATE TABLE t1(k INTEGER)");
        st.close();

        engine.close();

        NopDuckDbException e = assertThrows(NopDuckDbException.class, engine::openMemory);
        assertEquals(NopDuckDbErrors.ERR_DUCKDB_ENGINE_CLOSED.getErrorCode(), e.getErrorCode());
        // connection opened by the engine is closed with it
        assertThrows(Exception.class, conn::createStatement);
        // close is idempotent
        engine.close();
    }

    @Test
    @Timeout(60)
    public void testFileWorkingSetEndToEnd() throws Exception {
        Path dir = Files.createTempDirectory("duckdb-wi1-e2e");
        String dbFile = dir.resolve("work.duckdb").toString();

        DuckDbEngine writer = newEngine("128MB");
        try (Connection conn = writer.openFile(dbFile);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE notes(id INTEGER, name VARCHAR)");
            st.execute("INSERT INTO notes VALUES (1, 'alpha'), (2, 'beta')");
        } finally {
            writer.close();
        }

        // reopen after the writer engine closed: committed data persists (task-level working set)
        DuckDbEngine reader = newEngine("128MB");
        try (Connection conn = reader.openFile(dbFile);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*), sum(id) FROM notes")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
            assertEquals(3L, rs.getLong(2));
        } finally {
            reader.close();
        }
    }

    @Test
    @Timeout(60)
    public void testFingerprintConflictBetweenEngineInstances() throws Exception {
        Path dir = Files.createTempDirectory("duckdb-wi1-conflict");
        String dbFile = dir.resolve("conflict.duckdb").toString();

        DuckDbEngine first = newEngine("128MB");
        try {
            Connection held = first.openFile(dbFile);
            assertNotNull(held);

            DuckDbEngine second = newEngine("256MB");
            try {
                NopDuckDbException e = assertThrows(NopDuckDbException.class, () -> second.openFile(dbFile));
                assertEquals(NopDuckDbErrors.ERR_DUCKDB_CONFIG_CONFLICT.getErrorCode(), e.getErrorCode());
                assertEquals(dbFile, e.getParams().get(NopDuckDbErrors.ARG_FILE_PATH));
            } finally {
                second.close();
            }
        } finally {
            first.close();
        }

        // after the holder closed, reopening with a different config is legitimate (refs released)
        try (Connection conn = newEngine("256MB").openFile(dbFile)) {
            assertNotNull(conn);
        }
    }

    @Test
    @Timeout(60)
    public void testForeignConnectionConfigConflictTranslated() throws Exception {
        Path dir = Files.createTempDirectory("duckdb-wi1-foreign");
        String dbFile = dir.resolve("foreign.duckdb").toString();

        // a foreign component (e.g. a Hikari pool) holds the file with an explicit user
        Connection foreign = DriverManager.getConnection("jdbc:duckdb:" + dbFile, "otheruser", "");
        try {
            NopDuckDbException e = assertThrows(NopDuckDbException.class, () -> engine.openFile(dbFile));
            assertEquals(NopDuckDbErrors.ERR_DUCKDB_CONFIG_CONFLICT.getErrorCode(), e.getErrorCode());
            assertNotNull(e.getCause(), "original SQLException must be preserved as cause");
        } finally {
            foreign.close();
        }
    }

    @Test
    @Timeout(60)
    public void testNativeLoadFailureWrappedExplicitly() {
        // supported platform has a working native lib; simulate the load failure at the single
        // connection-creation boundary (DuckDBNative static init surfaces as ExceptionInInitializerError)
        DuckDbEngine broken = new BrokenNativeEngine();
        try {
            NopDuckDbException e = assertThrows(NopDuckDbException.class, broken::openMemory);
            assertEquals(NopDuckDbErrors.ERR_DUCKDB_NATIVE_LOAD_FAILED.getErrorCode(), e.getErrorCode());
            // no longer surfaces as a bare Error; cause chain preserved
            assertNotNull(e.getCause());
            String platform = (String) e.getParams().get(NopDuckDbErrors.ARG_PLATFORM);
            assertNotNull(platform);
            assertEquals(System.getProperty("os.name") + "/" + System.getProperty("os.arch"), platform);
        } finally {
            broken.close();
        }
    }

    /**
     * Simulates the DuckDBNative static-init failure at the driver-touch boundary
     * (the real boundary throws ExceptionInInitializerError, an Error, not SQLException).
     */
    static final class BrokenNativeEngine extends DuckDbEngine {
        @Override
        protected Connection newJdbcConnection(String url) throws SQLException {
            throw new ExceptionInInitializerError(new RuntimeException("simulated missing native library"));
        }
    }
}
