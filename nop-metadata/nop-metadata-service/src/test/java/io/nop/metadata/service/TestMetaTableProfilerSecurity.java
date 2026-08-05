package io.nop.metadata.service;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.catalog.MetaCatalogCollector;
import io.nop.metadata.service.profiling.MetaTableProfiler;
import io.nop.metadata.service.profiling.ProfilingSnapshot;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;
import io.nop.metadata.service.tableref.TableReference;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-01 对抗性回归测试：验证 3 个执行器（profiling/catalog/quality）的 schemaPattern 路径
 * 经过 {@code validateIdentifier} 校验，典型 SQL 注入 payload 必须显式失败（NopException，
 * 不传入 JDBC 层）。
 *
 * <p><b>接线验证</b>：测试通过反射调用 {@code normalizeSchema}（私有静态方法）验证
 * schemaPattern 校验确实在 buildFromClause 调用链上被触达，不只是 buildFromClause 方法存在。
 * 同时 {@link #testProfileEntryPointWired} 直接构造 {@link TableReference}（external 形态）
 * 调用 {@link MetaTableProfiler#profile}，验证 profile → buildFromClause → normalizeSchema 的
 * 完整调用链上 schemaPattern 注入 payload 必须显式失败（验证方法在运行时被调用，
 * 不只是方法存在）。
 */
public class TestMetaTableProfilerSecurity {

    /**
     * 反射调用 {@code normalizeSchema}，把 {@link InvocationTargetException} 解包为底层异常。
     * 这样 {@code assertThrows(NopException.class, ...)} 才能匹配底层 {@link NopException}。
     */
    private static String normalizeSchema(Method m, String schemaPattern) {
        try {
            return (String) m.invoke(null, schemaPattern);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, cause);
        } catch (IllegalAccessException e) {
            throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, e);
        }
    }

    /**
     * profiling 执行器：典型 SQL 注入 payload 在 schemaPattern 上必须显式失败。
     */
    @Test
    public void testProfilingSchemaPatternInjectionRejected() throws Exception {
        Method m = MetaTableProfiler.class.getDeclaredMethod("normalizeSchema", String.class);
        m.setAccessible(true);
        String[] payloads = {
                "x; DROP TABLE y",
                "mysql.user WHERE 1=1--",
                "schema UNION SELECT password FROM users",
                "schema' OR '1'='1",
                "schema/*comment*/",
                "schema; SHUTDOWN;"
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> normalizeSchema(m, payload),
                    "profiling schemaPattern injection payload must fail: " + payload);
            assertEquals(NopMetadataErrors.ERR_PROFILING_INVALID_IDENTIFIER.getErrorCode(),
                    ex.getErrorCode(),
                    "profiling schemaPattern injection must fail with ERR_PROFILING_INVALID_IDENTIFIER: " + payload);
        }
    }

    /**
     * catalog 执行器：典型 SQL 注入 payload 在 schemaPattern 上必须显式失败。
     */
    @Test
    public void testCatalogSchemaPatternInjectionRejected() throws Exception {
        Method m = MetaCatalogCollector.class.getDeclaredMethod("normalizeSchema", String.class);
        m.setAccessible(true);
        String[] payloads = {
                "x; DROP TABLE y",
                "mysql.user WHERE 1=1--",
                "schema UNION SELECT password FROM users",
                "schema' OR '1'='1",
                "schema/*comment*/"
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> normalizeSchema(m, payload),
                    "catalog schemaPattern injection payload must fail: " + payload);
            assertEquals(NopMetadataErrors.ERR_CATALOG_INVALID_IDENTIFIER.getErrorCode(),
                    ex.getErrorCode(),
                    "catalog schemaPattern injection must fail with ERR_CATALOG_INVALID_IDENTIFIER: " + payload);
        }
    }

    /**
     * quality 执行器：典型 SQL 注入 payload 在 schemaPattern 上必须显义失败。
     */
    @Test
    public void testQualitySchemaPatternInjectionRejected() throws Exception {
        Method m = MetaQualityRuleExecutor.class.getDeclaredMethod("normalizeSchema", String.class);
        m.setAccessible(true);
        String[] payloads = {
                "x; DROP TABLE y",
                "mysql.user WHERE 1=1--",
                "schema UNION SELECT password FROM users",
                "schema' OR '1'='1",
                "schema/*comment*/"
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> normalizeSchema(m, payload),
                    "quality schemaPattern injection payload must fail: " + payload);
            assertEquals(NopMetadataErrors.ERR_QUALITY_INVALID_IDENTIFIER.getErrorCode(),
                    ex.getErrorCode(),
                    "quality schemaPattern injection must fail with ERR_QUALITY_INVALID_IDENTIFIER: " + payload);
        }
    }

    /**
     * 合法 schemaPattern（简单标识符）必须通过校验，不被误拒（防过度防御）。
     */
    @Test
    public void testValidSchemaPatternAccepted() throws Exception {
        Method p = MetaTableProfiler.class.getDeclaredMethod("normalizeSchema", String.class);
        p.setAccessible(true);
        assertEquals("PUBLIC", normalizeSchema(p, "PUBLIC"));
        assertEquals("my_schema", normalizeSchema(p, " my_schema "));

        Method c = MetaCatalogCollector.class.getDeclaredMethod("normalizeSchema", String.class);
        c.setAccessible(true);
        assertEquals("PUBLIC", normalizeSchema(c, "PUBLIC"));

        Method q = MetaQualityRuleExecutor.class.getDeclaredMethod("normalizeSchema", String.class);
        q.setAccessible(true);
        assertEquals("PUBLIC", normalizeSchema(q, "PUBLIC"));

        // null/empty 通过（依赖连接默认 schema）
        assertEquals(null, normalizeSchema(p, null));
        assertEquals(null, normalizeSchema(p, ""));
        assertEquals(null, normalizeSchema(p, "   "));
    }

    /**
     * <b>接线验证（anti-hollow）</b>：profile() → buildFromClause → normalizeSchema 完整路径上，
     * schemaPattern 注入 payload 必须在 profile 入口处显式失败。
     *
     * <p>构造 external 形态 {@link TableReference}（物理表名通过校验），
     * schemaPattern 传注入 payload，profile 必须抛 NopException。
     * 验证 {@code validateIdentifier} 在运行时被调用链触达，不只是方法存在。
     */
    @Test
    public void testProfileEntryPointWired() {
        MetaTableProfiler profiler = new MetaTableProfiler();
        TableReference ref = new TableReference(
                TableReference.Kind.EXTERNAL, "mt-test", "T_VALID_TABLE", null,
                null, null, null, null);

        // profile 入口签名要求 Connection + DatabaseMetaData；用 null 占位（不会执行到 JDBC，
        // 因为 normalizeSchema 在 buildFromClause 调用前就会抛 ErrorCode）
        NopException ex = assertThrows(NopException.class,
                () -> profiler.profile(null, null, ref, "x; DROP TABLE y", null, "H2"),
                "profile entrypoint must reject schemaPattern injection before reaching JDBC layer");
        assertEquals(NopMetadataErrors.ERR_PROFILING_INVALID_IDENTIFIER.getErrorCode(),
                ex.getErrorCode(),
                "profile entrypoint schemaPattern injection must fail with ERR_PROFILING_INVALID_IDENTIFIER");
    }

    /**
     * <b>接线验证（catalog）</b>：collectForTable 入口拒绝 schemaPattern 注入（运行时触达）。
     */
    @Test
    public void testCatalogEntryPointWired() {
        MetaCatalogCollector collector = new MetaCatalogCollector();
        TableReference ref = new TableReference(
                TableReference.Kind.EXTERNAL, "mt-test", "T_VALID_TABLE", null,
                null, null, null, null);

        NopException ex = assertThrows(NopException.class,
                () -> collector.collectForTable(null, null, ref, "x; DROP TABLE y", "H2"),
                "collectForTable entrypoint must reject schemaPattern injection");
        assertEquals(NopMetadataErrors.ERR_CATALOG_INVALID_IDENTIFIER.getErrorCode(),
                ex.getErrorCode(),
                "collectForTable entrypoint schemaPattern injection must fail with ERR_CATALOG_INVALID_IDENTIFIER");
    }

    /**
     * <b>接线验证（quality）</b>：judge 入口（custom_sql 路径不经 schemaPattern，故测 volume 路径）拒绝
     * schemaPattern 注入。volume 路径走 buildFromClause → normalizeSchema，会先于 JDBC 校验。
     */
    @Test
    public void testQualityEntryPointWired() {
        MetaQualityRuleExecutor executor = new MetaQualityRuleExecutor();
        TableReference ref = new TableReference(
                TableReference.Kind.EXTERNAL, "mt-test", "T_VALID_TABLE", null,
                null, null, null, null);

        NopException ex = assertThrows(NopException.class,
                () -> executor.judge(null, ref, "x; DROP TABLE y",
                        "volume", "table", null, null, null, "H2"),
                "judge(volume) entrypoint must reject schemaPattern injection");
        assertEquals(NopMetadataErrors.ERR_QUALITY_INVALID_IDENTIFIER.getErrorCode(),
                ex.getErrorCode(),
                "judge entrypoint schemaPattern injection must fail with ERR_QUALITY_INVALID_IDENTIFIER");
    }

    /**
     * <b>防误拒</b>：profile 入口传合法 schemaPattern 必须进入 JDBC 路径（这里 Connection=null 会
     * 抛 NPE/其它异常，但 ErrorCode 必须不是 INVALID_IDENTIFIER，证明校验已通过）。
     */
    @Test
    public void testProfileValidSchemaNotRejectedByIdentifier() {
        MetaTableProfiler profiler = new MetaTableProfiler();
        TableReference ref = new TableReference(
                TableReference.Kind.EXTERNAL, "mt-test", "T_VALID_TABLE", null,
                null, null, null, null);
        Throwable thrown = null;
        try {
            profiler.profile(null, null, ref, "PUBLIC", null, "H2");
        } catch (Throwable t) {
            thrown = t;
        }
        assertTrue(thrown != null, "profile with null Connection must throw something");
        if (thrown instanceof NopException) {
            NopException ne = (NopException) thrown;
            assertTrue(!NopMetadataErrors.ERR_PROFILING_INVALID_IDENTIFIER.getErrorCode().equals(ne.getErrorCode()),
                    "valid schema PUBLIC must NOT be rejected by ERR_PROFILING_INVALID_IDENTIFIER");
        }
    }

    /**
     * MA6.2-002 回归：空串统计查询失败（真实 SQL 错误）不再静默吞掉——catch 分支必须记录
     * WARN 日志（可区分"真实错误"与"类型不支持"），且 emptyCount 记 0 不中断剖析。
     *
     * <p>用 {@link java.lang.reflect.Proxy} 包装真实 H2 连接，仅当 SQL 含 {@code WHERE C = ''}
     * （空串统计查询）时抛 SQLException；其余查询原样委托。Logback {@link ListAppender}
     * 断言 WARN 事件确实被发出（修复前该 catch 静默吞异常，无任何日志）。
     */
    @Test
    public void testEmptyCountQuerySqlErrorIsLoggedNotSilentlySwallowed() throws Exception {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MetaTableProfiler.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:prof_empty;DB_CLOSE_DELAY=-1", "sa", "");
                 Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE T_EMPTY (id INT NOT NULL, c VARCHAR(20))");
                st.execute("INSERT INTO T_EMPTY VALUES (1, '')");

                // 用真实 H2 连接跑 profile（走完整 JDBC 统计），但空串统计查询单独注入失败
                MetaTableProfiler profiler = new MetaTableProfiler();
                TableReference ref = new TableReference(
                        TableReference.Kind.EXTERNAL, "mt-test", "T_EMPTY", null,
                        null, null, null, null);

                ProfilingSnapshot snapshot = profileWithFailingEmptyCount(profiler, conn, ref);

                // 行为保持：emptyCount 记 0，剖析不中断
                assertEquals(0L, snapshot.getColumnStats().get(0).getEmptyCount(),
                        "emptyCount must be recorded as 0 on query failure");
                assertEquals(1L, snapshot.getRowCount(), "rowCount must still be profiled");
            }
            // 修复后：真实 SQL 错误必须留下 WARN 痕迹
            boolean warnLogged = appender.list.stream().anyMatch(e ->
                    e.getLevel() == ch.qos.logback.classic.Level.WARN
                            && e.getFormattedMessage().contains("empty-count-query-failed"));
            assertTrue(warnLogged,
                    "real SQL error in empty-count query must be logged with WARN (MA6.2-002), got: "
                            + appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                            .collect(java.util.stream.Collectors.toList()));
        } finally {
            logger.detachAppender(appender);
        }
    }

    /**
     * 反射调用私有 {@code profile}：真实连接执行到空串统计 SQL 时抛 SQLException（模拟
     * 列被删/权限等真实错误），验证 catch 分支语义。其余统计查询正常执行。
     */
    private static ProfilingSnapshot profileWithFailingEmptyCount(MetaTableProfiler profiler,
                                                                   Connection realConn,
                                                                   TableReference ref) throws Exception {
        Method m = MetaTableProfiler.class.getDeclaredMethod("profile",
                Connection.class, DatabaseMetaData.class, TableReference.class,
                String.class, String.class, String.class);
        m.setAccessible(true);

        // profile 只经 conn.createStatement() 执行查询——代理拦截 createStatement，
        // 返回"空串统计 SQL 抛 SQLException"的委托 Statement；其余方法原样委托真实连接。
        Connection failing = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("createStatement".equals(method.getName())) {
                        return new DelegatingStatement(realConn.createStatement()) {
                            @Override
                            public ResultSet executeQuery(String sql) throws SQLException {
                                if (sql.contains("= ''")) {
                                    throw new SQLException("T_EMPTY column dropped (simulated real error)");
                                }
                                return super.executeQuery(sql);
                            }
                        };
                    }
                    try {
                        return method.invoke(realConn, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });

        try {
            return (ProfilingSnapshot) m.invoke(profiler, failing, realConn.getMetaData(),
                    ref, "PUBLIC", null, "H2");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw NopException.adapt(cause);
        }
    }

    /** 委托 Statement 基类：executeQuery 子类化注入失败。 */
    private static class DelegatingStatement implements Statement {
        private final Statement delegate;

        DelegatingStatement(Statement delegate) {
            this.delegate = delegate;
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            return delegate.executeQuery(sql);
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            return delegate.executeUpdate(sql);
        }

        @Override
        public void close() throws SQLException {
            delegate.close();
        }

        @Override
        public int getMaxFieldSize() throws SQLException {
            return delegate.getMaxFieldSize();
        }

        @Override
        public void setMaxFieldSize(int max) throws SQLException {
            delegate.setMaxFieldSize(max);
        }

        @Override
        public int getMaxRows() throws SQLException {
            return delegate.getMaxRows();
        }

        @Override
        public void setMaxRows(int max) throws SQLException {
            delegate.setMaxRows(max);
        }

        @Override
        public void setEscapeProcessing(boolean enable) throws SQLException {
            delegate.setEscapeProcessing(enable);
        }

        @Override
        public int getQueryTimeout() throws SQLException {
            return delegate.getQueryTimeout();
        }

        @Override
        public void setQueryTimeout(int seconds) throws SQLException {
            delegate.setQueryTimeout(seconds);
        }

        @Override
        public void cancel() throws SQLException {
            delegate.cancel();
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
        public void setCursorName(String name) throws SQLException {
            delegate.setCursorName(name);
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            return delegate.execute(sql);
        }

        @Override
        public ResultSet getResultSet() throws SQLException {
            return delegate.getResultSet();
        }

        @Override
        public int getUpdateCount() throws SQLException {
            return delegate.getUpdateCount();
        }

        @Override
        public boolean getMoreResults() throws SQLException {
            return delegate.getMoreResults();
        }

        @Override
        public void setFetchDirection(int direction) throws SQLException {
            delegate.setFetchDirection(direction);
        }

        @Override
        public int getFetchDirection() throws SQLException {
            return delegate.getFetchDirection();
        }

        @Override
        public void setFetchSize(int rows) throws SQLException {
            delegate.setFetchSize(rows);
        }

        @Override
        public int getFetchSize() throws SQLException {
            return delegate.getFetchSize();
        }

        @Override
        public int getResultSetConcurrency() throws SQLException {
            return delegate.getResultSetConcurrency();
        }

        @Override
        public int getResultSetType() throws SQLException {
            return delegate.getResultSetType();
        }

        @Override
        public void addBatch(String sql) throws SQLException {
            delegate.addBatch(sql);
        }

        @Override
        public void clearBatch() throws SQLException {
            delegate.clearBatch();
        }

        @Override
        public int[] executeBatch() throws SQLException {
            return delegate.executeBatch();
        }

        @Override
        public Connection getConnection() throws SQLException {
            return delegate.getConnection();
        }

        @Override
        public boolean getMoreResults(int current) throws SQLException {
            return delegate.getMoreResults(current);
        }

        @Override
        public ResultSet getGeneratedKeys() throws SQLException {
            return delegate.getGeneratedKeys();
        }

        @Override
        public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
            return delegate.executeUpdate(sql, autoGeneratedKeys);
        }

        @Override
        public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
            return delegate.executeUpdate(sql, columnIndexes);
        }

        @Override
        public int executeUpdate(String sql, String[] columnNames) throws SQLException {
            return delegate.executeUpdate(sql, columnNames);
        }

        @Override
        public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
            return delegate.execute(sql, autoGeneratedKeys);
        }

        @Override
        public boolean execute(String sql, int[] columnIndexes) throws SQLException {
            return delegate.execute(sql, columnIndexes);
        }

        @Override
        public boolean execute(String sql, String[] columnNames) throws SQLException {
            return delegate.execute(sql, columnNames);
        }

        @Override
        public int getResultSetHoldability() throws SQLException {
            return delegate.getResultSetHoldability();
        }

        @Override
        public boolean isClosed() throws SQLException {
            return delegate.isClosed();
        }

        @Override
        public void setPoolable(boolean poolable) throws SQLException {
            delegate.setPoolable(poolable);
        }

        @Override
        public boolean isPoolable() throws SQLException {
            return delegate.isPoolable();
        }

        @Override
        public void closeOnCompletion() throws SQLException {
            delegate.closeOnCompletion();
        }

        @Override
        public boolean isCloseOnCompletion() throws SQLException {
            return delegate.isCloseOnCompletion();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
}
