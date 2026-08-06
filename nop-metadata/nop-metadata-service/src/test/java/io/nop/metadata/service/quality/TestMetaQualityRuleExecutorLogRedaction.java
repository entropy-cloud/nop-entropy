package io.nop.metadata.service.quality;

import io.nop.metadata.service.tableref.TableReference;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AR-16（R8.2）判别性测试：质量规则执行日志不再落盘完整 SQL / custom_sql 字面量——
 * 三处 {@code LOG.info}（queryLong / queryTimestamp / querySingleValue）只输出 sqlHash
 * （完整 SQL 降 DEBUG 级），敏感字面量（姓名/卡号等）不得出现在 INFO 级日志中。
 *
 * <p>沿 {@code TestNopMetaSearchProcessor} 既有 ListAppender 断言先例捕获日志事件；
 * 执行路径走 {@link MetaQualityRuleExecutor#judge} 公开入口 + mock JDBC（不依赖真实数据库）。
 */
public class TestMetaQualityRuleExecutorLogRedaction {

    private static final String SENSITIVE_PHONE = "13800138000";
    private static final String SENSITIVE_NAME = "张小明";

    /** custom_sql 路径（querySingleValue）：INFO 日志不含 SQL 原文（含敏感字面量）+ 含 sqlHash。 */
    @Test
    public void testCustomSqlInfoLogContainsHashNotLiteral() throws Exception {
        String sql = "SELECT COUNT(*) FROM users WHERE phone = '" + SENSITIVE_PHONE + "'";
        Connection conn = mockJdbcConnection(rs -> when(rs.getDouble(1)).thenReturn(0.0));

        LogCapture capture = new LogCapture();
        try {
            QualityRuleJudgment j = judge(conn, "custom_sql", null, sql);
            assertEquals("PASS", j.getStatus(),
                    "custom_sql returning 0 must pass (default expectPassWhen eq 0), got: " + j.getMessage()
                            + " details=" + j.getDetails());
        } finally {
            capture.restore();
        }

        assertNoInfoLiteral(capture, SENSITIVE_PHONE, sql);
        assertInfoHasSqlHash(capture, sql);
    }

    /** queryLong 路径（volume 规则 + sql 子查询引用，sourceSql 内嵌敏感字面量）。 */
    @Test
    public void testQueryLongInfoLogContainsHashNotLiteral() throws Exception {
        String sourceSql = "SELECT name FROM users WHERE phone = '" + SENSITIVE_PHONE + "'";
        Connection conn = mockJdbcConnection(rs -> when(rs.getLong(1)).thenReturn(5L));

        LogCapture capture = new LogCapture();
        try {
            QualityRuleJudgment j = judge(conn, "volume", null, sourceSql);
            assertEquals("PASS", j.getStatus(), "rowCount=5 with no min/max threshold must pass");
        } finally {
            capture.restore();
        }

        String fullSql = "SELECT COUNT(*) FROM (" + sourceSql + ") _t";
        assertNoInfoLiteral(capture, SENSITIVE_PHONE, fullSql);
        assertInfoHasSqlHash(capture, fullSql);
    }

    /** queryTimestamp 路径（freshness 规则 + sql 子查询引用）。 */
    @Test
    public void testQueryTimestampInfoLogContainsHashNotLiteral() throws Exception {
        String sourceSql = "SELECT ts FROM t WHERE name = '" + SENSITIVE_NAME + "'";
        Connection conn = mockJdbcConnection(rs ->
                when(rs.getTimestamp(1)).thenReturn(new Timestamp(System.currentTimeMillis() - 5 * 60_000L)));

        LogCapture capture = new LogCapture();
        try {
            QualityRuleJudgment j = judge(conn, "freshness", "{\"timestampColumn\":\"TS\",\"maxAgeMinutes\":60}",
                    sourceSql);
            assertEquals("PASS", j.getStatus(), "5-min-old timestamp within 60min maxAge must pass");
        } finally {
            capture.restore();
        }

        String fullSql = "SELECT MAX(TS) FROM (" + sourceSql + ") _t";
        assertNoInfoLiteral(capture, SENSITIVE_NAME, fullSql);
        assertInfoHasSqlHash(capture, fullSql);
    }

    private static void assertNoInfoLiteral(LogCapture capture, String sensitive, String fullSql) {
        boolean literalInInfo = capture.list.stream().anyMatch(e ->
                e.getLevel() == ch.qos.logback.classic.Level.INFO
                        && (e.getFormattedMessage().contains(sensitive)
                        || e.getFormattedMessage().contains(fullSql)));
        assertFalse(literalInInfo,
                "INFO-level quality rule log must not contain SQL literal/full SQL (AR-16), got: "
                        + capture.infoMessages());
    }

    private static void assertInfoHasSqlHash(LogCapture capture, String sql) {
        String hash = MetaQualityRuleExecutor.sqlHashOf(sql);
        boolean hashLogged = capture.list.stream().anyMatch(e ->
                e.getLevel() == ch.qos.logback.classic.Level.INFO
                        && e.getFormattedMessage().contains(hash));
        assertTrue(hashLogged,
                "INFO-level quality rule log must keep sqlHash for traceability (AR-16), got: "
                        + capture.infoMessages());
    }

    private static QualityRuleJudgment judge(Connection conn, String ruleType, String paramsJson, String sqlExpression) {
        MetaQualityRuleExecutor executor = new MetaQualityRuleExecutor();
        TableReference ref = new TableReference(
                TableReference.Kind.SQL, "mt-log-redact", null, sqlExpression,
                null, null, null, null);
        return executor.judge(conn, ref, null, ruleType, "table", paramsJson, sqlExpression, null, "H2");
    }

    @FunctionalInterface
    private interface ResultSetStub {
        void stub(ResultSet rs) throws Exception;
    }

    private static Connection mockJdbcConnection(ResultSetStub stub) throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement st = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(st);
        when(st.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        stub.stub(rs);
        return conn;
    }

    /** ListAppender 捕获 + 恢复（沿 TestNopMetaSearchProcessor 先例；DEBUG 级开启以便完整断言）。 */
    private static class LogCapture {
        final ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MetaQualityRuleExecutor.class);
        final ch.qos.logback.classic.Level oldLevel = logger.getLevel();
        final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        final java.util.List<ch.qos.logback.classic.spi.ILoggingEvent> list;

        LogCapture() {
            logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
            appender.start();
            logger.addAppender(appender);
            list = appender.list;
        }

        void restore() {
            logger.detachAppender(appender);
            logger.setLevel(oldLevel);
        }

        String infoMessages() {
            return list.stream()
                    .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.INFO)
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.toList()).toString();
        }
    }
}
