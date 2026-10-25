package io.nop.duckdb;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
 *
 * <p>Ledger retention policy note (5.6-W-01, plan 2502; design SSOT
 * {@code ai-dev/design/concurrency/ledger-retention-policy.md} §3.5 ① / 裁定⑥① / 勘误 7):
 * the OPEN_FILES registry and the per-instance connections list are <b>in-process lifecycle
 * pairing ledgers</b>, not DB tables with periodic growth. The family retention parameters
 * (retention-days / cleanup-interval / delete-batch-size / row-cap) are therefore NOT
 * applicable to this module. The family discipline 「上限+逐出+fail-fast」 projects here as
 * cleanup <b>form D - close bridging</b>: every connection handed out by {@link #openFile}
 * / {@link #openMemory} releases its own ledger entries (refs back to zero) when it is
 * closed, and {@link #close()} remains the idempotent fallback for consumers that never
 * close the returned connection. Do NOT apply DB-table cleanup shapes (deleteByQuery /
 * retention configs) to this module.
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

    /**
     * 5.6-W-01 close 桥接（清理形态 D，生命周期配对专用）：登记 TrackedConnection（保
     * {@link #close()} 幂等兜底语义零回退——tc.connection 记录的是原对象），但对外返回
     * close 时回调记账的 {@link BridgeConnection} 包装。消费方（含 DuckDbSqlTaskStep 的
     * try-with-resources 消费链）关闭包装即在锁内 untrack + 文件注册表 refs 释放，双载体
     * 账本随连接生命周期闭合，跨 engine 重开不再命中残留指纹（100% 假阳性
     * ERR_DUCKDB_CONFIG_CONFLICT 误拒面同批闭合）。
     */
    private synchronized Connection track(Connection conn, String normalizedFilePath) {
        TrackedConnection tc = new TrackedConnection(conn, normalizedFilePath);
        connections.add(tc);
        return new BridgeConnection(this, conn, normalizedFilePath, tc);
    }

    /**
     * close 桥接的记账半边：与 {@link #track} 同一 monitor，消费方关闭包装时把对应
     * TrackedConnection 移出 connections 账本。重复调用无害（List.remove 幂等）。
     */
    private synchronized void untrack(TrackedConnection tc) {
        connections.remove(tc);
    }

    // same-package test observation hooks（TestDuckDbLedgerCloseBridge_W_01）

    /** OPEN_FILES 注册表中指定规范化路径的当前 refs（null = 条目不存在/已随归零释放） */
    static Integer openFileRefCount(String normalizedPath) {
        FileEntry entry = OPEN_FILES.get(normalizedPath);
        return entry == null ? null : entry.refs.get();
    }

    /** connections 账本当前滞留的连接数 */
    int trackedConnectionCount() {
        synchronized (this) {
            return connections.size();
        }
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

    /**
     * 5.6-W-01 close 桥接包装（设计 ledger-retention-policy.md §3.4 成员 5 行，清理形态 D）：
     * 全方法委托原连接，仅 {@link #close()} 重载为「锁内 untrack + filePath != null 时
     * releaseOpen + 关闭原连接」。记账先行于物理关闭——后续任何失败不得使账本滞留（账本
     * 滞留即 C6-001 缺陷本体）；物理关闭经 {@link IoHelper#safeCloseObject}，失败记日志
     * 显式可见不静默吞掉（rule 24）。包装自身 close 幂等：记账由 CAS 保证单次执行，重复
     * close 仅触发 JDBC 层 no-op。
     *
     * <p>{@code engine.close()} 兜底语义零回退：{@link TrackedConnection} 仍持有原对象，
     * 从不关闭包装的消费方由 engine.close() 关闭原连接并释放 refs；已走包装 close 的连接
     * 已被 untrack，engine.close() 不会重复处理。
     */
    static final class BridgeConnection implements Connection {
        private final DuckDbEngine owner;
        private final Connection delegate;
        private final String filePath; // null = memory connection（无 OPEN_FILES 条目）
        private final TrackedConnection tracked;
        private final AtomicBoolean bridged = new AtomicBoolean(false);

        BridgeConnection(DuckDbEngine owner, Connection delegate, String filePath, TrackedConnection tracked) {
            this.owner = owner;
            this.delegate = delegate;
            this.filePath = filePath;
            this.tracked = tracked;
        }

        @Override
        public void close() {
            if (bridged.compareAndSet(false, true)) {
                // 记账先行：锁内 untrack + 文件注册表 refs 释放（refs 归零即条目移除）
                owner.untrack(tracked);
                if (filePath != null) {
                    releaseOpen(filePath);
                }
            }
            IoHelper.safeCloseObject(delegate);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this) || iface.isInstance(delegate);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this))
                return (T) this;
            if (iface.isInstance(delegate))
                return (T) delegate;
            return delegate.unwrap(iface);
        }

        @Override
        public Statement createStatement() throws SQLException {
            return delegate.createStatement();
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            return delegate.prepareStatement(sql);
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            return delegate.prepareCall(sql);
        }

        @Override
        public String nativeSQL(String sql) throws SQLException {
            return delegate.nativeSQL(sql);
        }

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            delegate.setAutoCommit(autoCommit);
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            return delegate.getAutoCommit();
        }

        @Override
        public void commit() throws SQLException {
            delegate.commit();
        }

        @Override
        public void rollback() throws SQLException {
            delegate.rollback();
        }

        @Override
        public boolean isClosed() throws SQLException {
            return delegate.isClosed();
        }

        @Override
        public DatabaseMetaData getMetaData() throws SQLException {
            return delegate.getMetaData();
        }

        @Override
        public void setReadOnly(boolean readOnly) throws SQLException {
            delegate.setReadOnly(readOnly);
        }

        @Override
        public boolean isReadOnly() throws SQLException {
            return delegate.isReadOnly();
        }

        @Override
        public void setCatalog(String catalog) throws SQLException {
            delegate.setCatalog(catalog);
        }

        @Override
        public String getCatalog() throws SQLException {
            return delegate.getCatalog();
        }

        @Override
        public void setTransactionIsolation(int level) throws SQLException {
            delegate.setTransactionIsolation(level);
        }

        @Override
        public int getTransactionIsolation() throws SQLException {
            return delegate.getTransactionIsolation();
        }

        @Override
        public SQLWarning getWarnings() throws SQLException {
            return delegate.getWarnings();
        }

        @Override
        public void clearWarnings() throws SQLException {
            delegate.clearWarnings();
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            return delegate.createStatement(resultSetType, resultSetConcurrency);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
                throws SQLException {
            return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
                throws SQLException {
            return delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public Map<String, Class<?>> getTypeMap() throws SQLException {
            return delegate.getTypeMap();
        }

        @Override
        public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
            delegate.setTypeMap(map);
        }

        @Override
        public void setHoldability(int holdability) throws SQLException {
            delegate.setHoldability(holdability);
        }

        @Override
        public int getHoldability() throws SQLException {
            return delegate.getHoldability();
        }

        @Override
        public Savepoint setSavepoint() throws SQLException {
            return delegate.setSavepoint();
        }

        @Override
        public Savepoint setSavepoint(String name) throws SQLException {
            return delegate.setSavepoint(name);
        }

        @Override
        public void rollback(Savepoint savepoint) throws SQLException {
            delegate.rollback(savepoint);
        }

        @Override
        public void releaseSavepoint(Savepoint savepoint) throws SQLException {
            delegate.releaseSavepoint(savepoint);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
                throws SQLException {
            return delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
                                                  int resultSetHoldability) throws SQLException {
            return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
                                             int resultSetHoldability) throws SQLException {
            return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            return delegate.prepareStatement(sql, autoGeneratedKeys);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            return delegate.prepareStatement(sql, columnIndexes);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            return delegate.prepareStatement(sql, columnNames);
        }

        @Override
        public Clob createClob() throws SQLException {
            return delegate.createClob();
        }

        @Override
        public Blob createBlob() throws SQLException {
            return delegate.createBlob();
        }

        @Override
        public NClob createNClob() throws SQLException {
            return delegate.createNClob();
        }

        @Override
        public SQLXML createSQLXML() throws SQLException {
            return delegate.createSQLXML();
        }

        @Override
        public boolean isValid(int timeout) throws SQLException {
            return delegate.isValid(timeout);
        }

        @Override
        public void setClientInfo(String name, String value) throws SQLClientInfoException {
            delegate.setClientInfo(name, value);
        }

        @Override
        public void setClientInfo(Properties properties) throws SQLClientInfoException {
            delegate.setClientInfo(properties);
        }

        @Override
        public String getClientInfo(String name) throws SQLException {
            return delegate.getClientInfo(name);
        }

        @Override
        public Properties getClientInfo() throws SQLException {
            return delegate.getClientInfo();
        }

        @Override
        public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
            return delegate.createArrayOf(typeName, elements);
        }

        @Override
        public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
            return delegate.createStruct(typeName, attributes);
        }

        @Override
        public void setSchema(String schema) throws SQLException {
            delegate.setSchema(schema);
        }

        @Override
        public String getSchema() throws SQLException {
            return delegate.getSchema();
        }

        @Override
        public void abort(java.util.concurrent.Executor executor) throws SQLException {
            delegate.abort(executor);
        }

        @Override
        public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) throws SQLException {
            delegate.setNetworkTimeout(executor, milliseconds);
        }

        @Override
        public int getNetworkTimeout() throws SQLException {
            return delegate.getNetworkTimeout();
        }
    }
}
