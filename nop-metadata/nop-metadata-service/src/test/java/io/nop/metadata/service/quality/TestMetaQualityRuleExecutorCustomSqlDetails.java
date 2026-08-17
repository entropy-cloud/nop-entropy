package io.nop.metadata.service.quality;

import io.nop.metadata.service.tableref.TableReference;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P2-07（plan 2026-08-16-0226-1）回归测试：custom_sql 判定的 QualityResult.details
 * **不再持久化 SQL 全文**——权威存储在规则本体（{@code NopMetaQualityRule.sqlExpression} 列
 * 优先 / params.sql JSON），details 只保留 {@code sqlHash}（SHA-256 短摘要）供审计追溯，
 * 与 AR-16 裁定的日志面"只记 sqlHash"语义对齐（持久化面/日志面同数据族同脱敏口径）。
 *
 * <p>PASS / FAIL / ERROR 三条判定路径全部断言 details 无 SQL 原文 + sqlHash 可与规则本体对账。
 * 执行路径走 {@link MetaQualityRuleExecutor#judge} 公开入口 + mock JDBC（沿
 * {@code TestMetaQualityRuleExecutorLogRedaction} 先例）。
 */
public class TestMetaQualityRuleExecutorCustomSqlDetails {

    private static final String RULE_SQL = "SELECT COUNT(*) FROM users WHERE phone = '13800138000'";

    /** PASS 路径：details 含 sqlHash（与规则 sqlExpression 对账）且不含 SQL 原文与 "sql" 键。 */
    @Test
    public void testPassPathDetailsCarrySqlHashNotFullSql() throws Exception {
        QualityRuleJudgment j = judgeCustomSql(mockConnectionReturning(0.0), RULE_SQL, null);
        assertEquals("PASS", j.getStatus(), "custom_sql returning 0 must pass (default eq 0): " + j.getMessage());
        assertDetailsCarryHashNotFullSql(j);
    }

    /** FAIL 路径：expectPassWhen 不满足 → FAIL，details 同样无 SQL 原文。 */
    @Test
    public void testFailPathDetailsCarrySqlHashNotFullSql() throws Exception {
        QualityRuleJudgment j = judgeCustomSql(mockConnectionReturning(0.0), RULE_SQL, "{\"expectPassWhen\":\"eq 1\"}");
        assertEquals("FAIL", j.getStatus(), "expectPassWhen=eq 1 with 0 must fail: " + j.getMessage());
        assertDetailsCarryHashNotFullSql(j);
    }

    /** ERROR 路径：SQL 执行失败 → ERROR，details 仍带 sqlHash、无 SQL 原文。 */
    @Test
    public void testErrorPathDetailsCarrySqlHashNotFullSql() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenThrow(new SQLException("syntax error near FROM"));
        QualityRuleJudgment j = judgeCustomSql(conn, RULE_SQL, null);
        assertEquals("ERROR", j.getStatus(), "SQL failure must produce ERROR judgment: " + j.getMessage());
        assertDetailsCarryHashNotFullSql(j);
    }

    /** params.sql 回退路径：sqlExpression 为 null 时取 params.sql，sqlHash 与规则本体 params.sql 对账。 */
    @Test
    public void testParamsSqlFallbackHashReconcilesWithRuleBody() throws Exception {
        String paramsJson = "{\"ruleKey\":\"r-params\",\"sql\":\"" + RULE_SQL.replace("\"", "'") + "\"}";
        QualityRuleJudgment j = judgeCustomSql(mockConnectionReturning(0.0), null, paramsJson);
        assertEquals("PASS", j.getStatus(), "params.sql fallback returning 0 must pass: " + j.getMessage());
        assertEquals(MetaQualityRuleExecutor.sqlHashOf(RULE_SQL), j.getDetails().get("sqlHash"),
                "sqlHash must reconcile with the rule body params.sql text");
        assertFalse(j.getDetails().containsKey("sql"), "details must not carry full SQL under 'sql' key");
        assertFalse(j.getDetails().toString().contains(RULE_SQL),
                "details must not embed the full SQL text anywhere: " + j.getDetails());
    }

    /** sqlExpression 优先路径对账：details.sqlHash == sqlHashOf(规则 sqlExpression 列)。 */
    @Test
    public void testSqlExpressionPriorityHashReconciles() throws Exception {
        QualityRuleJudgment j = judgeCustomSql(mockConnectionReturning(0.0), RULE_SQL,
                "{\"ruleKey\":\"r-expr\",\"sql\":\"SELECT 999\"}");
        assertEquals(MetaQualityRuleExecutor.sqlHashOf(RULE_SQL), j.getDetails().get("sqlHash"),
                "sqlHash must reconcile with the rule sqlExpression column (priority over params.sql)");
        assertFalse(MetaQualityRuleExecutor.sqlHashOf("SELECT 999").equals(j.getDetails().get("sqlHash")),
                "params.sql must NOT win over sqlExpression when both present");
    }

    private static void assertDetailsCarryHashNotFullSql(QualityRuleJudgment j) {
        assertEquals(MetaQualityRuleExecutor.sqlHashOf(RULE_SQL), j.getDetails().get("sqlHash"),
                "details must carry sqlHash reconciling with the rule body SQL");
        assertFalse(j.getDetails().containsKey("sql"),
                "details must not carry full SQL under 'sql' key (P2-07 removed details.sql)");
        assertFalse(j.getDetails().toString().contains(RULE_SQL),
                "details must not embed the full SQL text anywhere (incl. sensitive literals): " + j.getDetails());
        assertFalse(j.getDetails().toString().contains("13800138000"),
                "sensitive literals inside rule SQL must not leak into persisted details: " + j.getDetails());
    }

    private static QualityRuleJudgment judgeCustomSql(Connection conn, String sqlExpression, String paramsJson) {
        TableReference ref = new TableReference(TableReference.Kind.EXTERNAL, "mt-details",
                "T_SQL", null, null, null, null, null);
        return new MetaQualityRuleExecutor().judge(conn, ref, null, "custom_sql", "table",
                paramsJson, sqlExpression, null, "H2");
    }

    /** prepareStatement 正常、executeQuery 返回单行数值。 */
    private static Connection mockConnectionReturning(double value) throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getDouble(1)).thenReturn(value);
        when(rs.wasNull()).thenReturn(false);
        return conn;
    }
}
