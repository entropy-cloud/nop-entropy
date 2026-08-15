package io.nop.metadata.service.quality;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.tableref.TableReference;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P2-08（plan 2026-08-16-0226-1）quality 侧回归：{@code MetaQualityRuleExecutor.messageOf}
 * 收敛 jdbc: URL 脱敏过滤——单点覆盖该文件全部 messageOf 面（judgeCustomSql ERROR /
 * judgeRange ERROR / judgeRegex SKIP+ERROR / queryLong / queryTimestamp）。
 *
 * <p>执行路径走 {@link MetaQualityRuleExecutor#judge} 公开入口 + mock JDBC（沿
 * {@code TestMetaQualityRuleExecutorLogRedaction} / {@code TestMetaQualityRuleExecutorErrorParams}
 * 先例），非测内部私有方法。
 */
public class TestMetaQualityRuleExecutorMessageRedaction {

    /** custom_sql ERROR 面（judgeCustomSql :335 messageOf）：消息中 userinfo 口令 URL 脱敏。 */
    @Test
    public void testCustomSqlErrorMessageRedactsJdbcUrl() throws Exception {
        String driverMsg = "Cannot open connection to jdbc:mysql://root:secret@10.0.0.1:3306/prod (timeout)";
        Connection conn = mockFailingConnection(driverMsg);
        QualityRuleJudgment j = judgeCustomSql(conn, "SELECT 1");
        assertEquals("ERROR", j.getStatus(), "SQL failure must produce ERROR judgment");
        String msg = j.getMessage();
        assertFalse(msg.contains("secret"), "judgment message must not carry the password: " + msg);
        assertFalse(msg.contains("root:secret@"), "judgment message must not carry userinfo: " + msg);
        assertTrue(msg.contains("jdbc:mysql://10.0.0.1:3306/prod"),
                "redacted URL form retained for diagnostics: " + msg);
        assertTrue(msg.contains(NopMetadataErrors.ERR_QUALITY_RULE_EXEC_ISOLATED.getErrorCode()),
                "ErrorCode context retained: " + msg);
    }

    /** volume/queryLong 面（queryLong :690 messageOf → ERR_QUALITY_SQL_FAILED error param）：脱敏。 */
    @Test
    public void testQueryLongErrorParamRedactsJdbcUrl() throws Exception {
        String driverMsg = "Communications link failure to jdbc:mysql://root:secret@prod-db:3306/db";
        Connection conn = mockFailingConnection(driverMsg);
        TableReference ref = new TableReference(TableReference.Kind.EXTERNAL, "mt-redact",
                "T_VOL", null, null, null, null, null);
        NopException ex = assertThrows(NopException.class,
                () -> new MetaQualityRuleExecutor().judge(conn, ref, null, "volume", "table",
                        null, null, null, "H2"),
                "SQL failure must wrap into ERR_QUALITY_SQL_FAILED");
        assertEquals(NopMetadataErrors.ERR_QUALITY_SQL_FAILED.getErrorCode(), ex.getErrorCode());
        String error = String.valueOf(ex.getParam(NopMetadataErrors.ARG_ERROR));
        assertFalse(error.contains("secret"), "error param must not carry the password: " + error);
        assertTrue(error.contains("jdbc:mysql://prod-db:3306/db"),
                "redacted URL form retained in error param: " + error);
        assertFalse(String.valueOf(ex.getMessage()).contains("secret"),
                "rendered message must not carry the password: " + ex.getMessage());
    }

    /** 零误伤：不含 jdbc: 形态的普通驱动消息原样保留（诊断信息不丢）。 */
    @Test
    public void testPlainSqlErrorMessageUnchanged() throws Exception {
        Connection conn = mockFailingConnection("Table 'PROD.T_VOL' doesn't exist");
        QualityRuleJudgment j = judgeCustomSql(conn, "SELECT 1");
        assertEquals("ERROR", j.getStatus());
        assertTrue(j.getMessage().contains("Table 'PROD.T_VOL' doesn't exist"),
                "plain driver message must pass through verbatim: " + j.getMessage());
    }

    private static QualityRuleJudgment judgeCustomSql(Connection conn, String sql) {
        TableReference ref = new TableReference(TableReference.Kind.EXTERNAL, "mt-redact-sql",
                "T_SQL", null, null, null, null, null);
        return new MetaQualityRuleExecutor().judge(conn, ref, null, "custom_sql", "table",
                null, sql, null, "H2");
    }

    /** prepareStatement 阶段抛出携带受控消息的 SQLException。 */
    private static Connection mockFailingConnection(String message) throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenThrow(new SQLException(message));
        return conn;
    }
}
