
package io.nop.metadata.service.quality;

import io.nop.metadata.service.tableref.TableReference;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AR-11（R8.1）判别性测试：judgeRegex 方言判定从子串启发式收窄为签名集合匹配。
 *
 * <p>SKIP 仅保留给真实"方言不支持 REGEXP"场景（签名集合："not supported" / "unknown function" /
 * "syntax error at or near"（PostgreSQL REGEXP 运算符不支持的真实签名））；MySQL（支持 REGEXP）
 * 非法 pattern 报错 "Got error ... from regexp" 含 "regexp" 字面量、H2 非法 pattern 消息含 "syntax"
 * 字面量均为规则级正则错误 → 显式 ERROR，不再误判 SKIP 静默消失。
 *
 * <p>mock Connection（单元级，不依赖真实连接）走 {@link MetaQualityRuleExecutor#judge} 公开入口
 * 到 judgeRegex 的 prepareStatement 抛 SQLException 分支；合法 pattern PASS 用真实 H2 数据。
 */
public class TestMetaQualityRuleExecutorRegexDialect {

    private static final String DB_URL = "jdbc:h2:mem:meta_rgx_r81;DB_CLOSE_DELAY=-1";

    /** MySQL（支持 REGEXP）非法 pattern 报错含 "regexp" → 修复前被误判 SKIP（red）；修复后 ERROR。 */
    @Test
    public void testMySqlInvalidPatternErrorIsErrorNotSkip() {
        QualityRuleJudgment j = judgeWithSqlException(
                "Got error 'repetition-operator operand invalid' from regexp");
        assertEquals("ERROR", j.getStatus(),
                "MySQL invalid-pattern must be ERROR (was SKIP before AR-11: 'regexp' substring heuristic)");
        assertFalse(j.getDetails().containsKey("reason"),
                "ERROR path must not carry regexp-unsupported-dialect reason");
        assertTrue(j.getMessage().contains("regex SQL execution failed"),
                "ERROR message must surface SQL execution failure, got: " + j.getMessage());
    }

    /** "not supported" 签名 → 仍 SKIP + details.reason（真实方言不支持不翻 ERROR）。 */
    @Test
    public void testNotSupportedSignatureStillSkip() {
        QualityRuleJudgment j = judgeWithSqlException("REGEXP not supported by this database");
        assertEquals("SKIP", j.getStatus(), "'not supported' signature must stay SKIP");
        assertEquals("regexp-unsupported-dialect", j.getDetails().get("reason"));
    }

    /** "unknown function" 签名 → 仍 SKIP。 */
    @Test
    public void testUnknownFunctionSignatureStillSkip() {
        QualityRuleJudgment j = judgeWithSqlException("unknown function: REGEXP");
        assertEquals("SKIP", j.getStatus(), "'unknown function' signature must stay SKIP");
        assertEquals("regexp-unsupported-dialect", j.getDetails().get("reason"));
    }

    /** PostgreSQL 不支持 REGEXP 运算符的真实签名 "syntax error at or near" → 仍 SKIP。 */
    @Test
    public void testPostgresSyntaxErrorAtOrNearStillSkip() {
        QualityRuleJudgment j = judgeWithSqlException("ERROR: syntax error at or near \"REGEXP\"");
        assertEquals("SKIP", j.getStatus(),
                "PostgreSQL 'syntax error at or near' is a real dialect-unsupported signature, must stay SKIP");
        assertEquals("regexp-unsupported-dialect", j.getDetails().get("reason"));
    }

    /** H2 非法 pattern 消息含 "syntax" 字面量 → 裸 "syntax" 不得命中（H2 支持 REGEXP）→ ERROR。 */
    @Test
    public void testH2SyntaxLiteralPatternErrorIsErrorNotSkip() {
        QualityRuleJudgment j = judgeWithSqlException(
                "Syntax error in SQL expression: unexpected character '[' in pattern");
        assertEquals("ERROR", j.getStatus(),
                "bare 'syntax' substring must NOT match (H2 supports REGEXP; rule-level pattern error → ERROR)");
        assertFalse(j.getDetails().containsKey("reason"));
    }

    /** 合法 pattern + 真实 H2 数据 → PASS（正路径不回归）。 */
    @Test
    public void testLegalPatternPasses() throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, "sa", "")) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS T_RGX (ID INT NOT NULL, COL VARCHAR(20))");
                st.execute("DELETE FROM T_RGX");
                st.execute("INSERT INTO T_RGX VALUES (1, 'abc')");
                st.execute("INSERT INTO T_RGX VALUES (2, 'xyz')");
            }
            QualityRuleJudgment j = new MetaQualityRuleExecutor().judge(c, ref(),
                    null, "regex", "field", "{\"column\":\"COL\",\"pattern\":\"^[a-z]+$\"}", null, null, "H2");
            assertEquals("PASS", j.getStatus(), "legal pattern on H2 must PASS (no regression)");
        }
    }

    private static QualityRuleJudgment judgeWithSqlException(String message) {
        Connection conn = mock(Connection.class);
        try {
            when(conn.prepareStatement(anyString())).thenThrow(new SQLException(message));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return new MetaQualityRuleExecutor().judge(conn, ref(),
                null, "regex", "field", "{\"column\":\"COL\",\"pattern\":\"[a-z]+\"}", null, null, "H2");
    }

    private static TableReference ref() {
        return new TableReference(TableReference.Kind.EXTERNAL, "mt-rgx",
                "T_RGX", null, null, null, null, null);
    }
}
