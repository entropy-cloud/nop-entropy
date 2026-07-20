/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;
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
            assertEquals(MetaQualityRuleExecutor.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(),
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
