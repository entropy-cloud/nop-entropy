
package io.nop.metadata.service.quality;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.tableref.TableReference;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AR-13（R8.1）判别性测试：质量 SQL 错误码参数归属。
 *
 * <p>裁定（Decision）：queryLong/queryTimestamp 静态路径无 ruleKey 上下文——ErrorCode 声明
 * {@code {ruleKey}} → {@code {sqlHash}}（最小改动，占位可解析），throw 点 `.param(ARG_SQL_HASH,
 * sqlHashOf(sql))` 并移除 `.param("sql", sql)`（完整 SQL 经 NopException.getMessage 无条件拼入 params
 * 会随 scheduler/executor LOG.error 落日志，与 R8.2 AR-16 脱敏目标冲突）；custom_sql 路径 +
 * evalExpectPassWhen 有 ruleKey 可取 → 补 `.param(ARG_QUALITY_RULE_ID, ruleKey)`（替换字面量
 * {@code <evalExpectPassWhen>}）。
 */
public class TestMetaQualityRuleExecutorErrorParams {

    /** ERR_QUALITY_SQL_NO_ROW：message 渲染真实 sqlHash（非 {ruleKey} 字面空洞）+ 不含 SQL 原文。 */
    @Test
    public void testSqlNoRowErrorRendersRealSqlHashNotLiteralPlaceholder() {
        Connection conn = mockConnectionWithEmptyResult();
        NopException ex = assertThrows(NopException.class, () -> judgeVolume(conn),
                "empty ResultSet must fail-fast with ERR_QUALITY_SQL_NO_ROW");
        assertEquals(NopMetadataErrors.ERR_QUALITY_SQL_NO_ROW.getErrorCode(), ex.getErrorCode());

        String hash = MetaQualityRuleExecutor.sqlHashOf("SELECT COUNT(*) FROM T_VOL");
        assertTrue(ex.getMessage().contains(hash),
                "message must render the real sqlHash placeholder value, got: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("{ruleKey}"),
                "no literal {ruleKey} placeholder may remain, got: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("SELECT COUNT(*)"),
                "message must NOT embed the full SQL literal (redaction, aligned with R8.2 AR-16), got: "
                        + ex.getMessage());
        assertEquals(hash, String.valueOf(ex.getParam(NopMetadataErrors.ARG_SQL_HASH)),
                "sqlHash param must be the real hash of the failing SQL");
        assertEquals(null, ex.getParam("sql"),
                "raw 'sql' param must be removed (was set before AR-13)");
    }

    /** ERR_QUALITY_SQL_FAILED：sqlHash + error 参数 + cause 保留 + 无 SQL 原文。 */
    @Test
    public void testSqlFailedErrorCarriesSqlHashErrorAndCause() {
        SQLException cause = new SQLException("connection broken");
        Connection conn = mock(Connection.class);
        try {
            when(conn.prepareStatement(anyString())).thenThrow(cause);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        NopException ex = assertThrows(NopException.class, () -> judgeVolume(conn),
                "SQL failure must wrap into ERR_QUALITY_SQL_FAILED");
        assertEquals(NopMetadataErrors.ERR_QUALITY_SQL_FAILED.getErrorCode(), ex.getErrorCode());
        assertSame(cause, ex.getCause(), "original SQLException must be preserved in cause chain");
        assertEquals(MetaQualityRuleExecutor.sqlHashOf("SELECT COUNT(*) FROM T_VOL"),
                String.valueOf(ex.getParam(NopMetadataErrors.ARG_SQL_HASH)),
                "sqlHash param must be the real hash of the failing SQL");
        assertTrue(String.valueOf(ex.getParam(NopMetadataErrors.ARG_ERROR)).contains("connection broken"),
                "error param must carry the failure message");
        assertFalse(ex.getMessage().contains("SELECT COUNT(*)"),
                "message must NOT embed the full SQL literal (redaction), got: " + ex.getMessage());
        assertEquals(null, ex.getParam("sql"), "raw 'sql' param must be removed");
    }

    /**
     * expectPassWhen 非法配置（经 judge 公开入口 → judgeCustomSql → evalExpectPassWhen 调用点接线）：
     * 错误上下文必须为真实 ruleKey（非字面量 {@code <evalExpectPassWhen>}）。
     */
    @Test
    public void testExpectPassWhenInvalidCarriesRealRuleKey() {
        Connection conn = mockConnectionReturning(5.0);
        TableReference ref = new TableReference(TableReference.Kind.EXTERNAL, "mt-sql",
                "T_SQL", null, null, null, null, null);
        NopException ex = assertThrows(NopException.class,
                () -> new MetaQualityRuleExecutor().judge(conn, ref, null, "custom_sql", "table",
                        "{\"ruleKey\":\"r-custom\",\"sql\":\"SELECT 1\",\"expectPassWhen\":\"gt abc\"}",
                        null, null, "H2"),
                "invalid expectPassWhen must fail-fast with ERR_QUALITY_EXPECT_PASS_WHEN_INVALID");
        assertEquals(NopMetadataErrors.ERR_QUALITY_EXPECT_PASS_WHEN_INVALID.getErrorCode(), ex.getErrorCode());
        assertEquals("r-custom", String.valueOf(ex.getParam(NopMetadataErrors.ARG_QUALITY_RULE_ID)),
                "error context must carry the real ruleKey (was <evalExpectPassWhen> literal before AR-13)");
        assertFalse(String.valueOf(ex.getParam(NopMetadataErrors.ARG_QUALITY_RULE_ID)).contains("evalExpectPassWhen"),
                "no literal <evalExpectPassWhen> context may remain");
    }

    private static void judgeVolume(Connection conn) {
        TableReference ref = new TableReference(TableReference.Kind.EXTERNAL, "mt-vol",
                "T_VOL", null, null, null, null, null);
        new MetaQualityRuleExecutor().judge(conn, ref, null, "volume", "table", null, null, null, "H2");
    }

    /** prepareStatement 正常、executeQuery 返回空 ResultSet（触发 ERR_QUALITY_SQL_NO_ROW）。 */
    private static Connection mockConnectionWithEmptyResult() {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        try {
            when(conn.prepareStatement(anyString())).thenReturn(ps);
            when(ps.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return conn;
    }

    /** prepareStatement 正常、executeQuery 返回单行数值（custom_sql 返回 5.0）。 */
    private static Connection mockConnectionReturning(double value) {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        try {
            when(conn.prepareStatement(anyString())).thenReturn(ps);
            when(ps.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true);
            when(rs.getDouble(1)).thenReturn(value);
            when(rs.wasNull()).thenReturn(false);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return conn;
    }
}
