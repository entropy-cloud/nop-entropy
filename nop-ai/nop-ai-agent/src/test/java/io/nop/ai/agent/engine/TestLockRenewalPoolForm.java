package io.nop.ai.agent.engine;

import io.nop.ai.agent.runtime.lock.DbSessionTakeoverLock;
import io.nop.ai.agent.runtime.lock.ISessionTakeoverLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-MA-16 (plan 2555 裁定 F) carrier — renewal pool form:
 * <ul>
 *   <li>the self-created renewal scheduler is a MULTI-thread pool: one
 *       session's blocking JDBC renewal must not head-of-line-block another
 *       session's renewal (the pre-fix single-thread scheduler serialized
 *       them);</li>
 *   <li>the pool size is configurable ({@code nop.ai.agent.lock-renew-pool-size}
 *       convention — setter injection, default 2);</li>
 *   <li>{@code DbSessionTakeoverLock.tryRenew} applies a statement
 *       queryTimeout (config {@code nop.ai.agent.takeover-renew-query-timeout},
 *       seconds, default 5) — structure pin via a capturing JDBC stub.</li>
 * </ul>
 */
public class TestLockRenewalPoolForm {

    private final ConcurrentHashMap<String, AgentSessionLifecycle.CancelHandle> runningExecutions =
            new ConcurrentHashMap<>();

    private SessionLockRenewal renewal;
    private final java.util.List<Future<?>> handles = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Future<?> h : handles) {
            SessionLockRenewal.cancelLockRenewalQuietly(h);
        }
    }

    // ========================================================================
    // Multi-thread pool form: slow renewal in session A does not serialize
    // session B's renewal
    // ========================================================================

    @Test
    void slowRenewalDoesNotHeadOfLineBlockOtherSessions() throws Exception {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        config.setLockRenewIntervalMs(20); // fast tick
        CountDownLatch blockSessionA = new CountDownLatch(1);
        AtomicInteger renewA = new AtomicInteger();
        AtomicInteger renewB = new AtomicInteger();

        config.setSessionTakeoverLock(new ISessionTakeoverLock() {
            @Override
            public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
                return true;
            }

            @Override
            public boolean tryRenew(String sessionId, String ownerId, long leaseMs) {
                if ("sess-a".equals(sessionId)) {
                    try {
                        // Simulate the blocking JDBC renew body.
                        blockSessionA.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    renewA.incrementAndGet();
                    return true;
                }
                renewB.incrementAndGet();
                return true;
            }

            @Override
            public boolean release(String sessionId, String ownerId) {
                return true;
            }

            @Override
            public boolean isHeld(String sessionId) {
                return false;
            }
        });

        renewal = new SessionLockRenewal(config, runningExecutions);
        AgentSessionLifecycle.CancelHandle handleA =
                new AgentSessionLifecycle.CancelHandle(null, null);
        AgentSessionLifecycle.CancelHandle handleB =
                new AgentSessionLifecycle.CancelHandle(null, null);

        // renewOnceSafe skips sessions without a registered execution slot.
        runningExecutions.put("sess-a", handleA);
        runningExecutions.put("sess-b", handleB);
        handles.add(renewal.startLockRenewal(handleA, "sess-a", "owner"));
        handles.add(renewal.startLockRenewal(handleB, "sess-b", "owner"));

        // Session A's renewal thread is parked inside the blocking renew body;
        // session B's renewal must STILL advance (multi-thread pool form).
        long deadline = System.currentTimeMillis() + 20_000;
        while (renewB.get() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(renewB.get() >= 3,
                "session B's renewal must proceed while session A's renewal is blocked "
                        + "(head-of-line blocking = single-thread scheduler regression)");
        assertEquals(0, renewA.get(),
                "session A's renewal must still be parked inside its blocking body");
        blockSessionA.countDown();
    }

    @Test
    void poolSizeIsConfigurableWithSaneDefault() {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        renewal = new SessionLockRenewal(config, runningExecutions);
        assertEquals(2, renewal.getLockRenewPoolSize(),
                "default renewal pool core size (nop.ai.agent.lock-renew-pool-size convention)");
        renewal.setLockRenewPoolSize(4);
        assertEquals(4, renewal.getLockRenewPoolSize());
    }

    // ========================================================================
    // DbSessionTakeoverLock.tryRenew: statement queryTimeout structure pin
    // ========================================================================

    @Test
    void tryRenewAppliesStatementQueryTimeout() throws Exception {
        CapturingConnection connection = new CapturingConnection();
        DataSource ds = new SingleConnectionDataSource(connection);

        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(ds);
        assertEquals(5, lock.getTakeoverRenewQueryTimeoutSec(),
                "default takeover renew query timeout (nop.ai.agent.takeover-renew-query-timeout)");

        lock.tryRenew("sess-q", "owner", 60_000L);
        assertNotNull(connection.marker.queryTimeoutSeen,
                "tryRenew must set a statement queryTimeout (MA-16)");
        assertEquals(Integer.valueOf(5), connection.marker.queryTimeoutSeen,
                "the default query timeout is 5 seconds");

        lock.setTakeoverRenewQueryTimeoutSec(9);
        lock.tryRenew("sess-q", "owner", 60_000L);
        assertEquals(Integer.valueOf(9), connection.marker.queryTimeoutSeen,
                "the configured timeout (nop.ai.agent.takeover-renew-query-timeout) must be applied");
    }

    // ========================================================================
    // JDBC stubs (dynamic proxies — capture setQueryTimeout)
    // ========================================================================

    /** Holder for the captured queryTimeout value. */
    static final class CapturedStatementState {
        volatile Integer queryTimeoutSeen;
    }

    /** DataSource double handing out one stub connection. */
    static final class SingleConnectionDataSource implements DataSource {
        private final CapturingConnection connection;

        SingleConnectionDataSource(CapturingConnection connection) {
            this.connection = connection;
        }

        @Override public Connection getConnection() { return connection; }
        @Override public Connection getConnection(String username, String password) { return connection; }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { throw new UnsupportedOperationException(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    /**
     * Connection stub whose {@code prepareStatement} always returns a proxy
     * capturing {@code setQueryTimeout} into {@code marker}.
     */
    static final class CapturingConnection implements Connection {
        final CapturedStatementState marker = new CapturedStatementState();
        private final PreparedStatement statement =
                (PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                        CapturingConnection.class.getClassLoader(),
                        new Class<?>[]{PreparedStatement.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "setQueryTimeout":
                                    marker.queryTimeoutSeen = (Integer) args[0];
                                    return null;
                                case "executeUpdate":
                                    return 1;
                                case "close":
                                case "setAutoCommit":
                                case "commit":
                                case "rollback":
                                case "clearWarnings":
                                    return null;
                                case "isClosed":
                                case "isWrapperFor":
                                    return Boolean.FALSE;
                                case "unwrap":
                                    throw new SQLException("not a wrapper");
                                default:
                                    Class<?> rt = method.getReturnType();
                                    if (rt == boolean.class) return Boolean.FALSE;
                                    if (rt == int.class) return 0;
                                    if (rt == long.class) return 0L;
                                    return null;
                            }
                        });

        @Override public PreparedStatement prepareStatement(String sql) { return statement; }
        @Override public PreparedStatement prepareStatement(String sql, int a, int b) { return statement; }
        @Override public PreparedStatement prepareStatement(String sql, int a) { return statement; }
        @Override public PreparedStatement prepareStatement(String sql, int[] a) { return statement; }
        @Override public PreparedStatement prepareStatement(String sql, String[] a) { return statement; }
        @Override public PreparedStatement prepareStatement(String sql, int a, int b, int c) { return statement; }
        @Override public Statement createStatement() throws SQLException {
            return (Statement) java.lang.reflect.Proxy.newProxyInstance(
                    CapturingConnection.class.getClassLoader(),
                    new Class<?>[]{Statement.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "execute":
                                return Boolean.TRUE;
                            case "close":
                                return null;
                            case "isClosed":
                            case "isWrapperFor":
                                return Boolean.FALSE;
                            case "unwrap":
                                throw new SQLException("not a wrapper");
                            default:
                                Class<?> rt = method.getReturnType();
                                if (rt == boolean.class) return Boolean.FALSE;
                                if (rt == int.class) return 0;
                                if (rt == long.class) return 0L;
                                return null;
                        }
                    });
        }
        @Override public Statement createStatement(int a, int b, int c) throws SQLException { throw new SQLException("unsupported"); }
        @Override public Statement createStatement(int a, int b) throws SQLException { throw new SQLException("unsupported"); }
        @Override public String nativeSQL(String sql) throws SQLException { return sql; }
        @Override public CallableStatement prepareCall(String sql) throws SQLException { throw new SQLException("unsupported"); }
        @Override public CallableStatement prepareCall(String sql, int a, int b) throws SQLException { throw new SQLException("unsupported"); }
        @Override public CallableStatement prepareCall(String sql, int a, int b, int c) throws SQLException { throw new SQLException("unsupported"); }
        @Override public void setAutoCommit(boolean autoCommit) { }
        @Override public boolean getAutoCommit() { return true; }
        @Override public void commit() { }
        @Override public void rollback() { }
        @Override public void close() { }
        @Override public boolean isClosed() { return false; }
        @Override public java.sql.DatabaseMetaData getMetaData() throws SQLException { throw new SQLException("unsupported"); }
        @Override public void setReadOnly(boolean readOnly) { }
        @Override public boolean isReadOnly() { return false; }
        @Override public void setCatalog(String catalog) { }
        @Override public String getCatalog() { return null; }
        @Override public void setTransactionIsolation(int level) { }
        @Override public int getTransactionIsolation() { return Connection.TRANSACTION_READ_COMMITTED; }
        @Override public SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() { }
        @Override public java.util.Map<String, Class<?>> getTypeMap() { return new java.util.HashMap<>(); }
        @Override public void setTypeMap(java.util.Map<String, Class<?>> map) { }
        @Override public void setHoldability(int holdability) { }
        @Override public int getHoldability() { return 0; }
        @Override public java.sql.Savepoint setSavepoint() throws SQLException { throw new SQLException("unsupported"); }
        @Override public java.sql.Savepoint setSavepoint(String name) throws SQLException { throw new SQLException("unsupported"); }
        @Override public void rollback(java.sql.Savepoint savepoint) { }
        @Override public void releaseSavepoint(java.sql.Savepoint savepoint) { }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public void setClientInfo(String name, String value) { }
        @Override public String getClientInfo(String name) { return null; }
        @Override public Properties getClientInfo() { return new Properties(); }
        @Override public void setClientInfo(Properties properties) { }
        @Override public java.sql.Array createArrayOf(String typeName, Object[] elements) throws SQLException { throw new SQLException("unsupported"); }
        @Override public java.sql.Blob createBlob() throws SQLException { throw new SQLException("unsupported"); }
        @Override public java.sql.Clob createClob() throws SQLException { throw new SQLException("unsupported"); }
        @Override public java.sql.NClob createNClob() throws SQLException { throw new SQLException("unsupported"); }
        @Override public java.sql.SQLXML createSQLXML() throws SQLException { throw new SQLException("unsupported"); }
        @Override public boolean isValid(int timeout) { return true; }
        @Override public void setSchema(String schema) { }
        @Override public String getSchema() { return null; }
        @Override public void abort(java.util.concurrent.Executor executor) { }
        @Override public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) { }
        @Override public int getNetworkTimeout() { return 0; }
        @Override public java.sql.Struct createStruct(String typeName, Object[] attributes) throws SQLException { throw new SQLException("unsupported"); }
    }
}
