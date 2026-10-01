package io.nop.duckdb;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default {@link IDuckDbEngine} implementation.
 *
 * <p>Config keys (all optional, empty = do not apply the corresponding SET):
 * <ul>
 * <li>{@code nop.duckdb.memory-limit} - e.g. 256MB (DuckDB normalizes the value, e.g. 244.1 MiB)</li>
 * <li>{@code nop.duckdb.threads} - integer thread count</li>
 * <li>{@code nop.duckdb.temp-directory} - spill directory for out-of-core execution</li>
 * </ul>
 * The key set is closed: there is no fourth "unimplemented" key. A recognized key with an
 * invalid value fails fast with {@code nop.err.duckdb.invalid-config}.
 *
 * <p>Config is resolved once at bean creation; a running engine has a fixed configuration.
 */
public class DuckDbEngine implements IDuckDbEngine {
    private static final Logger LOG = LoggerFactory.getLogger(DuckDbEngine.class);

    private static final String DUCKDB_URL_PREFIX = "jdbc:duckdb:";
    private static final String DIFFERENT_CONFIG_MARKER = "different configuration";
    private static final String LOCK_MARKER = "Could not set lock on file";

    /**
     * JVM-wide registry enforcing config uniformity per absolute file path. Covers the case of
     * two engine instances with different configs opening the same file (foreign components such
     * as a Hikari pool are covered by translating DuckDB's own "different configuration" error).
     */
    private static final Map<String, FileEntry> OPEN_FILES = new ConcurrentHashMap<>();

    @InjectValue("@cfg:nop.duckdb.memory-limit|")
    protected String memoryLimit;

    @InjectValue("@cfg:nop.duckdb.threads|")
    protected String threads;

    @InjectValue("@cfg:nop.duckdb.temp-directory|")
    protected String tempDirectory;

    private final List<TrackedConnection> connections = new ArrayList<>();
    private volatile boolean closed;

    @Override
    public Connection openMemory() {
        checkClosed();
        Connection conn = openConnection(DUCKDB_URL_PREFIX, "<memory>");
        try {
            applySettings(conn);
        } catch (RuntimeException e) {
            IoHelper.safeCloseObject(conn);
            throw e;
        }
        return track(conn, null);
    }

    @Override
    public Connection openFile(String filePath) {
        checkClosed();
        Guard.notEmpty(filePath, "filePath");
        String normalized = new File(filePath).getAbsoluteFile().getPath();
        String fingerprint = configFingerprint();

        FileEntry entry = OPEN_FILES.get(normalized);
        if (entry != null && !entry.fingerprint.equals(fingerprint)) {
            throw (NopException) new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_CONFIG_CONFLICT)
                    .param(NopDuckDbErrors.ARG_FILE_PATH, filePath)
                    .param(NopDuckDbErrors.ARG_REASON, "file is already opened with engine config ["
                            + entry.fingerprint + "], requested [" + fingerprint + "]")
                    .bizFatal(true);
        }

        Connection conn = openConnection(DUCKDB_URL_PREFIX + filePath, filePath);
        try {
            applySettings(conn);
        } catch (RuntimeException e) {
            IoHelper.safeCloseObject(conn);
            throw e;
        }
        registerOpen(normalized, fingerprint);
        return track(conn, normalized);
    }

    @Override
    public void close() {
        List<TrackedConnection> toClose;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            toClose = new ArrayList<>(connections);
            connections.clear();
        }
        for (TrackedConnection tc : toClose) {
            IoHelper.safeCloseObject(tc.connection);
            if (tc.filePath != null) {
                releaseOpen(tc.filePath);
            }
        }
        LOG.debug("nop.duckdb.engine-closed:connections={}", toClose.size());
    }

    /**
     * Template method for connection creation. Translates failure shapes observed in the WI0
     * spike: DuckDB's own "different configuration" rejection becomes config-conflict, and any
     * non-SQLException throwable (DuckDBNative static init failures surface as
     * ExceptionInInitializerError, an Error - not SQLException) becomes native-load-failed.
     */
    protected Connection openConnection(String url, String label) {
        try {
            return newJdbcConnection(url);
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains(DIFFERENT_CONFIG_MARKER)) {
                // permanent: all connections to one file must share an identical config
                throw (NopException) new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_CONFIG_CONFLICT, e)
                        .param(NopDuckDbErrors.ARG_FILE_PATH, label)
                        .param(NopDuckDbErrors.ARG_REASON, msg)
                        .bizFatal(true);
            }
            if (msg != null && msg.contains(LOCK_MARKER)) {
                // permanent while the holder lives: single writer per file (WI0 Q3)
                throw (NopException) new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_FILE_LOCKED, e)
                        .param(NopDuckDbErrors.ARG_FILE_PATH, label)
                        .param(NopDuckDbErrors.ARG_REASON, msg)
                        .bizFatal(true);
            }
            throw new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_CONNECT_FAILED, e)
                    .param(NopDuckDbErrors.ARG_FILE_PATH, label)
                    .param(NopDuckDbErrors.ARG_REASON, String.valueOf(msg));
        } catch (Throwable t) {
            // permanent: missing platform native will not appear on retry
            throw (NopException) new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_NATIVE_LOAD_FAILED, t)
                    .param(NopDuckDbErrors.ARG_PLATFORM, platform())
                    .param(NopDuckDbErrors.ARG_REASON, t.getMessage() == null ? t.toString() : t.getMessage())
                    .bizFatal(true);
        }
    }

    /**
     * Single driver-touch boundary (first classload of DuckDBDriver happens inside).
     * Separate from {@link #openConnection(String, String)} so the failure translation
     * there can be tested on platforms with a working native library.
     */
    protected Connection newJdbcConnection(String url) throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void applySettings(Connection conn) {
        if (!StringHelper.isEmpty(memoryLimit)) {
            execSet(conn, "memory_limit", quote(memoryLimit));
        }
        if (!StringHelper.isEmpty(threads)) {
            if (!StringHelper.isNumber(threads)) {
                throw invalidConfig("threads", threads);
            }
            execSet(conn, "threads", threads);
        }
        if (!StringHelper.isEmpty(tempDirectory)) {
            execSet(conn, "temp_directory", quote(tempDirectory));
        }
    }

    private void execSet(Connection conn, String key, String value) {
        try (Statement st = conn.createStatement()) {
            st.execute("SET " + key + "=" + value);
        } catch (SQLException e) {
            // permanent: a rejected SET means the configured value is unusable, retry cannot fix it
            throw (NopException) new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_INVALID_CONFIG, e)
                    .param(NopDuckDbErrors.ARG_CONFIG_KEY, key)
                    .param(NopDuckDbErrors.ARG_CONFIG_VALUE, value)
                    .bizFatal(true);
        }
    }

    private NopException invalidConfig(String key, String value) {
        // permanent: retrying with the same config cannot succeed
        return (NopException) new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_INVALID_CONFIG)
                .param(NopDuckDbErrors.ARG_CONFIG_KEY, key)
                .param(NopDuckDbErrors.ARG_CONFIG_VALUE, value)
                .bizFatal(true);
    }

    private static String quote(String value) {
        return "'" + value + "'";
    }

    String configFingerprint() {
        // connection user is pinned to the JDBC default (no explicit user), included for clarity
        return "memory-limit=" + memoryLimit + ",threads=" + threads
                + ",temp-directory=" + tempDirectory + ",user=<default>";
    }

    private synchronized Connection track(Connection conn, String normalizedFilePath) {
        connections.add(new TrackedConnection(conn, normalizedFilePath));
        return conn;
    }

    private static void registerOpen(String normalizedPath, String fingerprint) {
        OPEN_FILES.compute(normalizedPath, (k, v) -> {
            if (v == null) {
                return new FileEntry(fingerprint);
            }
            v.refs.incrementAndGet();
            return v;
        });
    }

    private static void releaseOpen(String normalizedPath) {
        OPEN_FILES.compute(normalizedPath, (k, v) -> {
            if (v == null) {
                return null;
            }
            if (v.refs.decrementAndGet() <= 0) {
                return null;
            }
            return v;
        });
    }

    private void checkClosed() {
        if (closed) {
            throw (NopException) new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_ENGINE_CLOSED)
                    .bizFatal(true);
        }
    }

    private static String platform() {
        return System.getProperty("os.name") + "/" + System.getProperty("os.arch");
    }

    private static final class FileEntry {
        final String fingerprint;
        final AtomicInteger refs = new AtomicInteger(1);

        FileEntry(String fingerprint) {
            this.fingerprint = fingerprint;
        }
    }

    private static final class TrackedConnection {
        final Connection connection;
        final String filePath;

        TrackedConnection(Connection connection, String filePath) {
            this.connection = connection;
            this.filePath = filePath;
        }
    }
}
