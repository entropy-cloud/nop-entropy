package io.nop.metadata.service;

import io.nop.metadata.service.field.ResolvedTableField;
import io.nop.metadata.service.profiling.MetaTableProfiler;
import io.nop.metadata.service.profiling.ProfilingSnapshot;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;
import io.nop.metadata.service.query.MetaTableQueryExecutor;
import io.nop.metadata.service.tableref.TableReference;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P1-8 判别性测试（plan 2026-08-15-1913-1，AR-16 形态）：sql 路径（sql 视图 / sql 表剖析）执行日志
 * 不再落盘完整 SQL（内嵌 sourceSql 全文，可含敏感字面量）——{@code LOG.info} 只输出 sqlHash，
 * 全文降 DEBUG 级。
 *
 * <p>覆盖两条入口：
 * <ul>
 *   <li><b>queryTableData sql 视图路径</b>：{@link MetaTableQueryExecutor#buildSqlSelectSql} +
 *       {@link MetaTableQueryExecutor#executeQuery}（queryTableData 对 sql 表的执行路径）+ mock JDBC；</li>
 *   <li><b>profileTable sql 路径</b>：{@link MetaTableProfiler#profile}（SQL 形态 {@link TableReference}）
 *       + 真实 H2 内存库（沿 {@code TestMetaTableProfilerSecurity} 既有 H2 先例）。</li>
 * </ul>
 *
 * <p>断言形态沿 {@code TestMetaQualityRuleExecutorLogRedaction} 既有 ListAppender 先例。
 */
public class TestSqlPathLogRedaction {

    private static final String SENSITIVE_NAME = "张小明";

    // ===== queryTableData sql 视图路径（MetaTableQueryExecutor.executeQuery）=====

    /**
     * sql 视图查询：INFO 日志不含 sourceSql 全文/敏感字面量，含 sqlHash；DEBUG 保留全文（降级非删除）。
     */
    @Test
    public void testQueryTableDataSqlViewInfoLogContainsHashNotLiteral() throws Exception {
        String sourceSql = "SELECT name FROM users WHERE name = '" + SENSITIVE_NAME + "'";
        String sql = MetaTableQueryExecutor.buildSqlSelectSql(sourceSql, null, null, null, "H2");
        Connection conn = mockQueryConnection();

        LogCapture capture = new LogCapture(MetaTableQueryExecutor.class);
        try {
            List<Map<String, Object>> rows = MetaTableQueryExecutor.executeQuery(conn, sql, null, null, null);
            assertTrue(rows.isEmpty(), "mock JDBC with 0 columns must return empty rows");
        } finally {
            capture.restore();
        }

        assertNoInfoLiteral(capture, SENSITIVE_NAME, sourceSql);
        assertInfoHasSqlHash(capture, sql);
        assertDebugKeepsFullSql(capture, sql);
    }

    // ===== profileTable sql 路径（MetaTableProfiler.profile，SQL 形态 TableReference）=====

    /**
     * sql 表剖析：所有 profiling 聚合查询（queryLong / queryNullableDouble / queryNullableLong /
     * queryString 四个 helper）的 INFO 日志不含 sourceSql 全文/敏感字面量，含 sqlHash；DEBUG 保留全文。
     */
    @Test
    public void testProfileTableSqlPathInfoLogContainsHashNotLiteral() throws Exception {
        String sourceSql = "SELECT name AS NAME FROM T_LOG_REDACT WHERE name = '" + SENSITIVE_NAME + "'";

        LogCapture capture = new LogCapture(MetaTableProfiler.class);
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:prof_log_redact;DB_CLOSE_DELAY=-1", "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE T_LOG_REDACT (id INT NOT NULL, name VARCHAR(50))");
            st.execute("INSERT INTO T_LOG_REDACT VALUES (1, '" + SENSITIVE_NAME + "')");

            MetaTableProfiler profiler = new MetaTableProfiler();
            TableReference ref = new TableReference(
                    TableReference.Kind.SQL, "mt-log-redact", null, sourceSql,
                    null, null, null,
                    Collections.singletonList(new ResolvedTableField("NAME", ResolvedTableField.SOURCE_SQL, null)));

            ProfilingSnapshot snapshot = profiler.profile(conn, conn.getMetaData(), ref, null, null, "H2");
            assertTrue(snapshot.getRowCount() == 1L, "H2 must profile 1 row, got: " + snapshot.getRowCount());
        } finally {
            capture.restore();
        }

        String fullCountSql = "SELECT COUNT(*) FROM (" + sourceSql + ") _t";
        assertNoInfoLiteral(capture, SENSITIVE_NAME, sourceSql);
        assertInfoHasSqlHash(capture, fullCountSql);
        assertDebugKeepsFullSql(capture, fullCountSql);
    }

    // ===== 断言助手（沿 TestMetaQualityRuleExecutorLogRedaction 先例）=====

    private static void assertNoInfoLiteral(LogCapture capture, String sensitive, String sourceSql) {
        boolean literalInInfo = capture.list.stream().anyMatch(e ->
                e.getLevel() == ch.qos.logback.classic.Level.INFO
                        && (e.getFormattedMessage().contains(sensitive)
                        || e.getFormattedMessage().contains(sourceSql)));
        assertFalse(literalInInfo,
                "INFO-level sql-path log must not contain SQL literal/full sourceSql (P1-8, AR-16), got: "
                        + capture.infoMessages());
    }

    private static void assertInfoHasSqlHash(LogCapture capture, String sql) {
        String hash = MetaQualityRuleExecutor.sqlHashOf(sql);
        boolean hashLogged = capture.list.stream().anyMatch(e ->
                e.getLevel() == ch.qos.logback.classic.Level.INFO
                        && e.getFormattedMessage().contains(hash));
        assertTrue(hashLogged,
                "INFO-level sql-path log must keep sqlHash for traceability (P1-8, AR-16), got: "
                        + capture.infoMessages());
    }

    /** 全文降级而非删除：DEBUG 级保留完整 SQL（排障通道）。 */
    private static void assertDebugKeepsFullSql(LogCapture capture, String sql) {
        boolean debugHasFull = capture.list.stream().anyMatch(e ->
                e.getLevel() == ch.qos.logback.classic.Level.DEBUG
                        && e.getFormattedMessage().contains(sql));
        assertTrue(debugHasFull,
                "DEBUG-level sql-path log must keep full SQL for troubleshooting (P1-8, AR-16), got: "
                        + capture.list.stream()
                        .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.DEBUG)
                        .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                        .collect(Collectors.toList()));
    }

    /** mock JDBC：0 列 ResultSet（executeQuery 的 getMetaData/getColumnCount 路径可达、无行返回）。 */
    private static Connection mockQueryConnection() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement st = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(conn.prepareStatement(anyString())).thenReturn(st);
        when(st.executeQuery()).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(0);
        when(rs.next()).thenReturn(false);
        when(rs.getObject(anyInt())).thenReturn(null);
        return conn;
    }

    /** ListAppender 捕获 + 恢复（沿 TestMetaQualityRuleExecutorLogRedaction 先例；DEBUG 级开启以便完整断言）。 */
    private static class LogCapture {
        final ch.qos.logback.classic.Logger logger;
        final ch.qos.logback.classic.Level savedLevel;
        final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        final java.util.List<ch.qos.logback.classic.spi.ILoggingEvent> list;

        LogCapture(Class<?> loggerClass) {
            logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(loggerClass);
            savedLevel = logger.getLevel();
            logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
            appender.start();
            logger.addAppender(appender);
            list = appender.list;
        }

        void restore() {
            logger.detachAppender(appender);
            logger.setLevel(savedLevel);
        }

        String infoMessages() {
            return list.stream()
                    .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.INFO)
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.toList()).toString();
        }
    }
}
