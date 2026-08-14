package io.nop.metadata.service;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.tableref.TableReference;
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

    // ===== R6.1（P2-10）：黑名单 6 缺项补全 + judge 公开入口端到端接线验证 =====

    /**
     * R6.1（P2-10）：6 个补全关键字（PG_READ_BINARY_FILE / RUNSCRIPT / PG_LS_LOGDIR / PG_LS_WALDIR /
     * PG_STAT_FILE / SCRIPT）从 {@code judge} 公开入口（null Connection）到
     * {@code ERR_QUALITY_CUSTOM_SQL_BLOCKED} 抛错链路全部成立。
     *
     * <p>不选 {@code SCRIPT 'CREATE TABLE ...'}——字符串内 CREATE 已触发既有黑名单，无法唯一钉住
     * SCRIPT 条目；本组 {@code SCRIPT TO} 向量唯一钉住 SCRIPT。
     */
    @Test
    public void testR61NewKeywordsBlockedViaJudgeEntry() {
        String[] payloads = {
                "SELECT pg_read_binary_file('/etc/passwd')",
                "RUNSCRIPT FROM '/tmp/evil.sql'",
                "SELECT PG_LS_LOGDIR()",
                "SELECT PG_LS_WALDIR()",
                "SELECT PG_STAT_FILE('/etc/passwd')",
                "SCRIPT TO '/tmp/backup.sql'"
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> judgeCustomSqlViaPublicEntry(payload),
                    "R6.1 new keyword must be blocked via judge entry: " + payload);
            assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(),
                    ex.getErrorCode(),
                    "must throw ERR_QUALITY_CUSTOM_SQL_BLOCKED via judge entry: " + payload);
            // 接线验证：sqlHash 参数仅在 judgeCustomSql 内（沙箱校验前 :271-272）写入——异常携带该参数
            // 证明调用链确实经 judge → judgeCustomSql → validateCustomSqlSandbox（null conn 下错误码
            // 从沙箱校验分支抛出），而非测试直接构造错误码
            assertNotNull(ex.getParam("sqlHash"),
                    "sqlHash param must be present (proves sandbox check reached from judgeCustomSql): " + payload);
            String reason = String.valueOf(ex.getParam("reason"));
            assertTrue(reason.contains("forbidden keyword"),
                    "reason must mention 'forbidden keyword': " + reason + " (payload=" + payload + ")");
        }
    }

    /** judge 公开入口（9 参）执行 custom_sql 路径：null conn——沙箱校验在触连前抛错即可达错误码。 */
    private static void judgeCustomSqlViaPublicEntry(String sql) {
        MetaQualityRuleExecutor executor = new MetaQualityRuleExecutor();
        TableReference ref = new TableReference(
                TableReference.Kind.EXTERNAL, "mt-test", "T_VALID_TABLE", null,
                null, null, null, null);
        executor.judge(null, ref, null, "custom_sql", "table", null, sql, null, null);
    }

    /** fail-closed 语义固化：字符串字面量内含黑名单 token（'UNION'）同样被拒（token 级匹配，不误放行）。 */
    @Test
    public void testUnionInsideStringLiteralBlockedFailClosed() {
        NopException ex = assertThrows(NopException.class,
                () -> validateCustomSqlSandbox("SELECT 'UNION'"),
                "UNION token inside string literal is rejected (fail-closed, documented behavior)");
        assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    // ===== AR-04/AR-05（plan-2026-08-06-0553-2）：DML/TCL 族 + H2 文件读写族拦截 =====

    /**
     * AR-04：DML 族（INSERT/UPDATE/DELETE/MERGE/REPLACE）全部被拒——规则 SQL 不得修改外部数据源数据
     * （MySQL/PG 驱动"先执行后报错"语义使篡改生效）。
     */
    @Test
    public void testDmlStatementsBlocked() {
        String[] payloads = {
                "DELETE FROM t",                                     // DELETE
                "UPDATE t SET a=1",                                  // UPDATE
                "INSERT INTO t SELECT * FROM s",                     // INSERT（外带写入形态）
                "MERGE INTO t USING s ON t.id=s.id WHEN MATCHED THEN UPDATE SET t.a=s.a",  // MERGE
                "REPLACE INTO t SELECT * FROM s"                     // REPLACE
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> validateCustomSqlSandbox(payload),
                    "DML statement must be blocked: " + payload);
            assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(),
                    ex.getErrorCode(),
                    "must throw ERR_QUALITY_CUSTOM_SQL_BLOCKED for payload: " + payload);
            String reason = String.valueOf(ex.getParam("reason"));
            assertTrue(reason.contains("forbidden keyword"),
                    "reason must mention 'forbidden keyword': " + reason + " (payload=" + payload + ")");
            assertNotNull(ex.getParam("sqlHash"),
                    "sqlHash param must be present for audit (payload=" + payload + ")");
        }
    }

    /**
     * AR-04：CTE 包装的 DML（{@code WITH x AS (DELETE FROM t) SELECT 1}）经 {@code judge} 公开入口被拒
     * （token 化后 DELETE 暴露）——沿 {@code testR61NewKeywordsBlockedViaJudgeEntry} 接线先例，
     * sqlHash 参数证明调用链经 judge → judgeCustomSql → validateCustomSqlSandbox。
     */
    @Test
    public void testCteWrappedDmlBlockedViaJudgeEntry() {
        NopException ex = assertThrows(NopException.class,
                () -> judgeCustomSqlViaPublicEntry("WITH x AS (DELETE FROM t) SELECT 1"),
                "CTE-wrapped DML must be blocked via judge entry");
        assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(), ex.getErrorCode());
        assertNotNull(ex.getParam("sqlHash"),
                "sqlHash proves sandbox check reached from judgeCustomSql (CTE-DML)");
        String reason = String.valueOf(ex.getParam("reason"));
        assertTrue(reason.contains("forbidden keyword"),
                "reason must mention 'forbidden keyword': " + reason);
    }

    /**
     * AR-04：TCL 族（COMMIT/ROLLBACK/SAVEPOINT/SET/TRANSACTION）+ RENAME/LOCK/UNLOCK 全部被拒
     * （黑名单条目逐项对齐 {@code ExpressionMeasureValidator.KEYWORD_BLACKLIST}）。
     */
    @Test
    public void testTclAndRenameLockUnlockBlocked() {
        String[] payloads = {
                "COMMIT",                                            // COMMIT
                "ROLLBACK",                                          // ROLLBACK
                "SAVEPOINT sp1",                                     // SAVEPOINT
                "SET TRANSACTION ISOLATION LEVEL SERIALIZABLE",     // SET / TRANSACTION
                "START TRANSACTION",                                 // TRANSACTION（唯一钉住）
                "RENAME TABLE a TO b",                               // RENAME（唯一钉住）
                "LOCK TABLE t IN EXCLUSIVE MODE",                    // LOCK（唯一钉住）
                "UNLOCK TABLES"                                      // UNLOCK（唯一钉住）
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> validateCustomSqlSandbox(payload),
                    "TCL/RENAME/LOCK/UNLOCK must be blocked: " + payload);
            assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(),
                    ex.getErrorCode(),
                    "must throw ERR_QUALITY_CUSTOM_SQL_BLOCKED for payload: " + payload);
        }
    }

    /**
     * AR-05：H2 文件读写族（FILE_WRITE/BACKUP/CSVWRITE + LOAD XML 序列）全部被拒——宿主文件读写面封堵。
     */
    @Test
    public void testH2FileFamilyBlocked() {
        String[] payloads = {
                "SELECT FILE_WRITE('/tmp/x','data')",               // FILE_WRITE
                "BACKUP TO '/tmp/b'",                                // BACKUP
                "SELECT CSVWRITE('/tmp/x','SELECT * FROM t')",      // CSVWRITE
                "LOAD XML INFILE '/tmp/x'"                           // LOAD XML 序列
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> validateCustomSqlSandbox(payload),
                    "H2 file-family keyword must be blocked: " + payload);
            assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(),
                    ex.getErrorCode(),
                    "must throw ERR_QUALITY_CUSTOM_SQL_BLOCKED for payload: " + payload);
            assertNotNull(ex.getParam("sqlHash"),
                    "sqlHash param must be present for audit (payload=" + payload + ")");
        }
    }

    /**
     * AR-05：H2 文件读族（FILE_READ/CSVREAD）经 {@code judge} 公开入口被拒——{@code SELECT FILE_READ('/etc/passwd')}
     * 类规则可读应用宿主机任意文件并回显，必须封堵；sqlHash 参数提供接线证据。
     */
    @Test
    public void testH2FileReadBlockedViaJudgeEntry() {
        String[] payloads = {
                "SELECT FILE_READ('/etc/passwd')",
                "SELECT * FROM CSVREAD('/etc/passwd')"
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> judgeCustomSqlViaPublicEntry(payload),
                    "H2 file-read keyword must be blocked via judge entry: " + payload);
            assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(),
                    ex.getErrorCode(),
                    "must throw ERR_QUALITY_CUSTOM_SQL_BLOCKED via judge entry: " + payload);
            assertNotNull(ex.getParam("sqlHash"),
                    "sqlHash proves sandbox check reached from judgeCustomSql (payload=" + payload + ")");
            String reason = String.valueOf(ex.getParam("reason"));
            assertTrue(reason.contains("forbidden keyword"),
                    "reason must mention 'forbidden keyword': " + reason + " (payload=" + payload + ")");
        }
    }

    /**
     * AR-04/AR-05 反例：合法 SELECT（不含黑名单词的形态）不被误杀——tokenizer 不剥离字符串字面量
     * （javadoc :323-324 + {@code testUnionInsideStringLiteralBlockedFailClosed} 钉死的既有语义），
     * 故反例一律避开"关键字样字面量"，采用无黑名单词形态。
     */
    @Test
    public void testSafeSqlWithoutBlacklistedWordsAllowed() {
        String[] safePayloads = {
                "SELECT updated_at FROM t WHERE updated_at >= '2026-01-01'",
                "SELECT COUNT(*) FROM ext_sql_t WHERE 1=0",
                "SELECT id FROM ext_sql_t"
        };
        for (String payload : safePayloads) {
            validateCustomSqlSandbox(payload);  // 不抛异常即通过
        }
    }

    /** 普通注释（-- 行注释 / 块注释）中的危险词随注释剥离，安全语句不被误杀。 */
    @Test
    public void testSafeSqlWithPlainCommentAllowed() {
        validateCustomSqlSandbox("SELECT COUNT(*) FROM orders -- daily count\n");
        validateCustomSqlSandbox("SELECT COUNT(*) FROM orders /* daily count */");
    }

    // ===== F9（plan 2026-08-14-1133-1）：PG 脚本执行 / 时序攻击 / 系统目录族 + WITH CTE =====

    /**
     * <b>F9 adversarial</b>：5 个新增关键字（{@code DO}/{@code WITH}/{@code PG_CATALOG}/{@code PG_SLEEP}/
     * {@code PG_STAT_USER_TABLES}）的 custom_sql payload 必须被 fail-fast 拒绝。
     *
     * <p>每个 payload 唯一钉住一个新增条目（不与既有黑名单词重叠）：
     * <ul>
     *   <li>{@code DO $$ BEGIN PERFORM 1 END $$}——PG 执行匿名 PL/pgSQL 代码块（DO 语句，RCE 等价面）</li>
     *   <li>{@code WITH t AS (SELECT 1) SELECT * FROM t}——CTE 包装（F9 tradeoff：合法分析型 CTE 亦被阻断）</li>
     *   <li>{@code SELECT PG_SLEEP(5)}——PG 时序盲注 / DoS 函数</li>
     *   <li>{@code SELECT * FROM PG_CATALOG.PG_DATABASE}——PG 系统目录 schema 信息泄漏</li>
     *   <li>{@code SELECT * FROM PG_STAT_USER_TABLES}——PG 系统统计视图信息泄漏</li>
     * </ul>
     */
    @Test
    public void testF9NewKeywordsBlocked() {
        String[] payloads = {
                "DO $$ BEGIN PERFORM 1 END $$",
                "WITH t AS (SELECT 1) SELECT * FROM t",
                "SELECT PG_SLEEP(5)",
                "SELECT * FROM PG_CATALOG.PG_DATABASE",
                "SELECT * FROM PG_STAT_USER_TABLES"
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> validateCustomSqlSandbox(payload),
                    "F9: newly added keyword must be blocked: " + payload);
            assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(),
                    ex.getErrorCode(),
                    "must throw ERR_QUALITY_CUSTOM_SQL_BLOCKED for payload: " + payload);
            String reason = String.valueOf(ex.getParam("reason"));
            assertTrue(reason.contains("forbidden keyword"),
                    "reason must mention 'forbidden keyword': " + reason + " (payload=" + payload + ")");
            assertNotNull(ex.getParam("sqlHash"),
                    "sqlHash param must be present for audit (payload=" + payload + ")");
        }
    }

    /**
     * <b>F9 接线验证</b>：新增关键字经 {@code judge} 公开入口（null Connection）到
     * {@code ERR_QUALITY_CUSTOM_SQL_BLOCKED} 抛错链路成立——sqlHash 参数证明调用链经
     * judge → judgeCustomSql → validateCustomSqlSandbox（沿 {@code testR61NewKeywordsBlockedViaJudgeEntry}
     * 与 {@code testCteWrappedDmlBlockedViaJudgeEntry} 先例）。
     */
    @Test
    public void testF9NewKeywordsBlockedViaJudgeEntry() {
        String[] payloads = {
                "SELECT PG_SLEEP(5)",
                "DO $$ BEGIN END $$"
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> judgeCustomSqlViaPublicEntry(payload),
                    "F9: new keyword must be blocked via judge entry: " + payload);
            assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(),
                    ex.getErrorCode(),
                    "must throw ERR_QUALITY_CUSTOM_SQL_BLOCKED via judge entry: " + payload);
            assertNotNull(ex.getParam("sqlHash"),
                    "sqlHash proves sandbox check reached from judgeCustomSql (payload=" + payload + ")");
        }
    }

    /**
     * F9 WITH CTE false-positive tradeoff 回归：合法分析型 CTE 查询被阻断（over-block，与 fail-closed 哲学一致）。
     * 合法 CTE 需求应改用子查询或专用 measure 表达式通道。本测试钉死 tradeoff 行为，防止未来"善意"放开 WITH
     * 而丢失 CTE 包装 DML/递归攻击面的拦截。
     */
    @Test
    public void testF9WithCteFalsePositiveTradeoff() {
        NopException ex = assertThrows(NopException.class,
                () -> validateCustomSqlSandbox("WITH regional_sales AS (SELECT region, SUM(amount) FROM orders GROUP BY region) SELECT * FROM regional_sales"),
                "F9: legitimate analytical CTE is blocked (documented over-block tradeoff)");
        assertEquals(NopMetadataErrors.ERR_QUALITY_CUSTOM_SQL_BLOCKED.getErrorCode(), ex.getErrorCode());
        String reason = String.valueOf(ex.getParam("reason"));
        assertTrue(reason.contains("WITH"),
                "F9: CTE block must identify WITH keyword: " + reason);
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
