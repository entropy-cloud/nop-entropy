package io.nop.metadata.service.quality;

import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.tableref.TableReference;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P2-13（plan 2026-08-16-0226-2 Phase 3）回归：`throw new SQLException` 哨兵消除后，
 * range/regex COUNT(*) 无行分支仍显式产出 ERROR 判定（非降级 PASS/SKIP、非静默），
 * judgment 输出与重构前 catch 路径逐字段等价（status/message/details）。
 *
 * <p>重构前该分支借哨兵异常进入 catch（SQLException 语义 = SQL 层故障被挪用为业务分支信号）；
 * 重构后无行是显式业务分支——真实 SQLException 仍走既有 catch（另有
 * {@code TestMetaQualityRuleExecutorErrorParams} 覆盖）。
 */
public class TestMetaQualityRuleExecutorNoRowBranch {

    /** range COUNT(*) 无行 → ERROR 判定，message 与原哨兵 catch 路径逐字节一致。 */
    @Test
    public void testRangeNoRowProducesExplicitErrorJudgment() {
        Connection conn = mockConnectionReturningNoRow();
        QualityRuleJudgment j = new MetaQualityRuleExecutor().judge(conn, externalRef("T_RANGE"), null,
                "range", "table", "{\"column\":\"amount\",\"min\":0,\"max\":10}", null, null, "H2");

        assertEquals("ERROR", j.getStatus(), "no-row must be an explicit ERROR judgment, not PASS/SKIP");
        assertEquals("range SQL execution failed: ["
                        + NopMetadataErrors.ERR_QUALITY_RULE_EXEC_ISOLATED.getErrorCode()
                        + "] range COUNT(*) returned no row",
                j.getMessage(), "message must equal the pre-refactor sentinel-catch output verbatim");
        assertEquals(null, j.getActualValue(),
                "actualValue must stay unset (pre-refactor returned before set)");
        assertEquals(null, j.getExpectedValue(),
                "expectedValue must stay unset (pre-refactor returned before set)");
        assertEquals(10.0, ((Number) j.getDetails().get("max")).doubleValue(),
                "details must keep min/max identity params");
        assertEquals(0.0, ((Number) j.getDetails().get("min")).doubleValue());
    }

    /** regex COUNT(*) 无行 → ERROR 判定（不经 isRegexpUnsupported 的 SKIP 分支）。 */
    @Test
    public void testRegexNoRowProducesExplicitErrorJudgment() {
        Connection conn = mockConnectionReturningNoRow();
        QualityRuleJudgment j = new MetaQualityRuleExecutor().judge(conn, externalRef("T_REGEX"), null,
                "regex", "table", "{\"column\":\"code\",\"pattern\":\"^[a-z]+$\"}", null, null, "H2");

        assertEquals("ERROR", j.getStatus(), "no-row must be an explicit ERROR judgment, not SKIP");
        assertEquals("regex SQL execution failed: ["
                        + NopMetadataErrors.ERR_QUALITY_RULE_EXEC_ISOLATED.getErrorCode()
                        + "] regex COUNT(*) returned no row",
                j.getMessage(), "message must equal the pre-refactor sentinel-catch output verbatim");
        assertEquals("^[a-z]+$", j.getDetails().get("pattern"), "details must keep pattern identity param");
        assertTrue(j.getDetails().get("reason") == null,
                "no regexp-unsupported SKIP reason may be set (no-row is not a dialect issue)");
    }

    private static TableReference externalRef(String tableName) {
        return new TableReference(TableReference.Kind.EXTERNAL, "mt-" + tableName.toLowerCase(),
                tableName, null, null, null, null, null);
    }

    /** prepareStatement 正常、executeQuery 返回无行的 ResultSet（COUNT(*) no-row 分支）。 */
    private static Connection mockConnectionReturningNoRow() {
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
}
