package io.nop.metadata.service;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 维度13-03 回归测试：custom_sql 规则的 SQL 内容沙箱化。
 *
 * <p>核心防御：{@code MetaQualityRuleExecutor.judgeCustomSql} 在执行用户配置 SQL 前，
 * 通过 {@link MetaQualityRuleExecutor#validateCustomSqlSandbox} 拒绝含危险关键字的 SQL：
 * <ul>
 *   <li>分号（多语句/stacked queries）</li>
 *   <li>{@code UNION}（跨表读取）</li>
 *   <li>{@code INTO OUTFILE} / {@code INTO DUMPFILE}（文件写入）</li>
 *   <li>{@code LOAD DATA} / {@code LOAD_FILE}（文件读取）</li>
 *   <li>{@code CALL} / {@code EXEC}（存储过程调用）</li>
 *   <li>DDL/DCL: {@code SHUTDOWN} / {@code DROP} / {@code TRUNCATE} / {@code ALTER} / {@code CREATE} / {@code GRANT}</li>
 * </ul>
 *
 * <p><b>关键认知</b>：PreparedStatement 不解决 custom_sql 注入（SQL 文本本身是用户配置），
 * 沙箱白名单是唯一可控的注入面收口。
 *
 * <p>同时验证 {@code sqlHash}（SHA-256 短摘要）被写入 details 供审计追溯。
 */
public class TestMetaQualityRuleExecutorCustomSqlSandbox {

    /** 典型 custom_sql 注入 payload 必须被白名单拒绝。 */
    @Test
    public void testDangerousKeywordsBlocked() {
        String[] dangerousPayloads = {
                "SELECT 1; DROP TABLE users",                                  // 分号 + DROP
                "SELECT * FROM users UNION SELECT password FROM mysql.user",   // UNION
                "SELECT * FROM users INTO OUTFILE '/tmp/leak'",                // INTO OUTFILE
                "SELECT * FROM users INTO DUMPFILE '/tmp/leak'",               // INTO DUMPFILE
                "LOAD DATA INFILE '/tmp/leak' INTO TABLE users",               // LOAD DATA
                "SELECT LOAD_FILE('/etc/passwd')",                              // LOAD_FILE
                "CALL admin_procedure()",                                       // CALL
                "EXEC admin_procedure",                                         // EXEC
                "SHUTDOWN",                                                     // SHUTDOWN
                "DROP TABLE users",                                             // DROP
                "TRUNCATE TABLE audit_log",                                     // TRUNCATE
                "ALTER TABLE users ADD COLUMN x INT",                           // ALTER
                "CREATE TABLE evil (x INT)",                                    // CREATE
                "GRANT ALL ON *.* TO 'evil'@'%'",                               // GRANT
                "REVOKE ALL ON *.* FROM 'admin'@'%'",                           // REVOKE
                "SELECT * FROM INFORMATION_SCHEMA.tables",                      // INFORMATION_SCHEMA
                "SELECT host FROM mysql.user"                                   // mysql.user
        };
        for (String payload : dangerousPayloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> validateCustomSqlSandbox(payload),
                    "custom_sql sandbox must reject dangerous payload: " + payload);
            assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(),
                    ex.getErrorCode(),
                    "must throw ERR_QUALITY_CUSTOM_SQL_BLOCKED for payload: " + payload);
            // reason 参数包含 "forbidden keyword"，便于运维定位
            String reason = String.valueOf(ex.getParam("reason"));
            assertTrue(reason.contains("forbidden keyword"),
                    "reason must mention 'forbidden keyword': " + reason + " (payload=" + payload + ")");
            // sqlHash 参数非空（审计追溯）
            assertNotNull(ex.getParam("sqlHash"),
                    "sqlHash param must be present for audit (payload=" + payload + ")");
        }
    }

    /** 安全的 custom_sql 不被白名单误杀。 */
    @Test
    public void testSafeCustomSqlAllowed() {
        String[] safePayloads = {
                "SELECT COUNT(*) FROM orders",
                "SELECT MAX(amount) FROM orders WHERE region = 'east'",
                "SELECT SUM(price * qty) FROM order_items",
                "SELECT AVG(score) FROM reviews",
                "  SELECT 1  "   // 前后空白应被 trim
        };
        for (String payload : safePayloads) {
            validateCustomSqlSandbox(payload);  // 不抛异常即通过
        }
    }

    /** 大小写不敏感：lowercase / mixed case 关键字都被拒绝。 */
    @Test
    public void testCaseInsensitiveMatching() {
        String[] mixedCasePayloads = {
                "select * from users union select password from mysql.user",   // 全小写
                "Select * From users Union Select 1",                          // 首字母大写
                "SELECT * FROM X; drop table Y"                                // 混合
        };
        for (String payload : mixedCasePayloads) {
            assertThrows(NopException.class,
                    () -> validateCustomSqlSandbox(payload),
                    "case-insensitive match must reject: " + payload);
        }
    }

    // ===== MA7.1-02：黑名单绕过变体回归 =====

    /** 空白变体（tab/多空格/注释分隔）必须命中 INTO OUTFILE token 序列。 */
    @Test
    public void testWhitespaceVariantsBlocked() {
        String[] variants = {
                "SELECT * INTO\tOUTFILE '/tmp/x'",
                "SELECT * INTO  OUTFILE '/tmp/x'",
                "SELECT * INTO/**/OUTFILE '/tmp/x'",
                "SELECT * INTO OUTFILE '/tmp/x'"
        };
        for (String payload : variants) {
            NopException ex = assertThrows(NopException.class,
                    () -> validateCustomSqlSandbox(payload),
                    "whitespace/comment variant must be rejected: " + payload);
            assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(),
                    ex.getErrorCode(),
                    "must throw ERR_QUALITY_CUSTOM_SQL_BLOCKED for variant: " + payload);
        }
    }

    /** 反引号限定名变体（`mysql`.`user`）必须命中 MYSQL.USER token 序列。 */
    @Test
    public void testBacktickQualifiedNameBlocked() {
        NopException ex = assertThrows(NopException.class,
                () -> validateCustomSqlSandbox("SELECT * FROM `mysql`.`user`"),
                "backtick-qualified mysql.user must be rejected");
        assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    /** 新增关键字：COPY（PG 服务端文件写）必须命中。 */
    @Test
    public void testCopyStatementBlocked() {
        NopException ex = assertThrows(NopException.class,
                () -> validateCustomSqlSandbox("COPY orders TO '/tmp/export.csv' WITH (FORMAT csv)"),
                "PG COPY statement must be rejected");
        assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    /** 新增关键字：PG_READ_FILE / PG_LS_DIR / SYS_EXEC 缺项补齐。 */
    @Test
    public void testMissingKeywordsNowBlocked() {
        String[] payloads = {
                "SELECT pg_read_file('/etc/passwd')",
                "SELECT pg_ls_dir('/')",
                "SELECT sys_exec('id')"
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> validateCustomSqlSandbox(payload),
                    "newly added keyword must be blocked: " + payload);
            assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(), ex.getErrorCode());
        }
    }

    /** MySQL 可执行注释（/*! 开头）显式拒绝（剥离后校验 = 绕过）。 */
    @Test
    public void testExecutableCommentBlocked() {
        NopException ex = assertThrows(NopException.class,
                () -> validateCustomSqlSandbox("SELECT 1 /*!50000 UNION SELECT 2*/"),
                "executable comment must be rejected");
        assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    /** fail-closed 语义固化：字符串字面量内含黑名单 token（'UNION'）同样被拒（token 级匹配，不误放行）。 */
    @Test
    public void testUnionInsideStringLiteralBlockedFailClosed() {
        NopException ex = assertThrows(NopException.class,
                () -> validateCustomSqlSandbox("SELECT 'UNION'"),
                "UNION token inside string literal is rejected (fail-closed, documented behavior)");
        assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    /** 普通注释（-- 行注释 / 块注释）中的危险词随注释剥离，安全语句不被误杀。 */
    @Test
    public void testSafeSqlWithPlainCommentAllowed() {
        validateCustomSqlSandbox("SELECT COUNT(*) FROM orders -- daily count\n");
        validateCustomSqlSandbox("SELECT COUNT(*) FROM orders /* daily count */");
    }

    /** sqlHash 稳定性：相同 SQL 产出相同 hash；不同 SQL 产出不同 hash（审计追溯基础）。 */
    @Test
    public void testSqlHashStability() {
        String h1 = MetaQualityRuleExecutor.sqlHashOf("SELECT COUNT(*) FROM users");
        String h2 = MetaQualityRuleExecutor.sqlHashOf("SELECT COUNT(*) FROM users");
        String h3 = MetaQualityRuleExecutor.sqlHashOf("SELECT COUNT(*) FROM orders");
        assertNotNull(h1);
        assertEquals(h1, h2, "same SQL → same hash");
        assertNotEquals(h1, h3, "different SQL → different hash");
        assertEquals(16, h1.length(), "sqlHash is 16-char short digest");
    }

    /** null SQL → null hash（不抛异常）。 */
    @Test
    public void testSqlHashNullSqlReturnsNull() {
        assertEquals(null, MetaQualityRuleExecutor.sqlHashOf(null));
    }

    /** 反射调用 {@code validateCustomSqlSandbox}（包内可见，避免依赖 JDBC 连接的 judge 入口）。 */
    private static void validateCustomSqlSandbox(String sql) {
        try {
            Method m = MetaQualityRuleExecutor.class.getDeclaredMethod(
                    "validateCustomSqlSandbox", String.class, String.class, String.class);
            m.setAccessible(true);
            String sqlHash = MetaQualityRuleExecutor.sqlHashOf(sql);
            Map<String, Object> params = new HashMap<>();
            params.put("ruleKey", "test-rule");
            m.invoke(null, sql, "test-rule", sqlHash);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, cause);
        } catch (Exception e) {
            throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, e);
        }
    }
}
