package io.nop.metadata.service.field;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.query.MetaAggregationExecutor;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 expression 型 Measure 文本校验器（§4.4.2 D12.3，plan 2026-07-18-1400-1 Phase 1）：
 * 静态校验（关键字 / 函数黑名单 + 标识符白名单 + 字面量参数绑定 + parse 结构）+ dialect-specific 函数支持检查。
 *
 * <p>纯单元测试（无 Spring 容器），覆盖 plan Phase 1 Exit Criteria：
 * 成功路径（≥3 类典型表达式）+ unparseable + unsafe + too-long + 参数绑定正确性。
 */
public class TestExpressionMeasureValidator {

    private static final String MT = "mt-test";
    private static final String MN = "m-test";

    // ============================================================
    // 成功路径：≥3 类典型表达式
    // ============================================================

    /** 算术表达式：{@code AMOUNT * 2}（数值字面量参数绑定）。 */
    @Test
    public void testArithmeticExpressionSuccess() {
        ExpressionMeasureValidator.ValidatedExpression r =
                ExpressionMeasureValidator.validateStatic("AMOUNT * 2",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        assertEquals("AMOUNT * ?", r.sqlFragment);
        assertEquals(1, r.params.size());
        assertEquals(new java.math.BigDecimal("2"), r.params.get(0));
        assertTrue(r.identifiers.contains("AMOUNT"));
        assertTrue(r.functions.isEmpty());
    }

    /** CASE WHEN：{@code CASE WHEN status='released' THEN 1 ELSE 0 END}（字符串 + 数值字面量 + 查询关键字）。 */
    @Test
    public void testCaseWhenExpressionSuccess() {
        ExpressionMeasureValidator.ValidatedExpression r =
                ExpressionMeasureValidator.validateStatic("CASE WHEN status='released' THEN 1 ELSE 0 END",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        // CASE/WHEN/THEN/ELSE/END 为允许的查询关键字；status 标识符；'released' 字符串字面量；1/0 数值字面量
        assertEquals("CASE WHEN status = ? THEN ? ELSE ? END", r.sqlFragment);
        assertEquals(3, r.params.size());
        assertEquals("released", r.params.get(0));
        assertEquals(new java.math.BigDecimal("1"), r.params.get(1));
        assertEquals(new java.math.BigDecimal("0"), r.params.get(2));
        assertTrue(r.identifiers.contains("status"));
    }

    /** STDDEV_SAMP 函数：{@code STDDEV_SAMP(amount)}（dialect-independent 函数支持）。 */
    @Test
    public void testStddevSampExpressionSuccess() {
        ExpressionMeasureValidator.ValidatedExpression r =
                ExpressionMeasureValidator.validateStatic("STDDEV_SAMP(amount)",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        assertEquals("STDDEV_SAMP ( amount )", r.sqlFragment);
        assertTrue(r.functions.contains("STDDEV_SAMP"));
        assertTrue(r.identifiers.contains("amount"));
        // dialect check：H2/PostgreSQL 应支持
        assertDoesNotThrow(() -> ExpressionMeasureValidator.checkDialectSupported(r, "H2", MT, MN));
        assertDoesNotThrow(() -> ExpressionMeasureValidator.checkDialectSupported(r, "PostgreSQL", MT, MN));
    }

    /** DATE_TRUNC：{@code DATE_TRUNC('month', created_at)}（dialect-specific 后续检查）。 */
    @Test
    public void testDateTruncExpressionSuccess() {
        ExpressionMeasureValidator.ValidatedExpression r =
                ExpressionMeasureValidator.validateStatic("DATE_TRUNC('month', created_at)",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        assertEquals("DATE_TRUNC ( ? , created_at )", r.sqlFragment);
        assertEquals(1, r.params.size());
        assertEquals("month", r.params.get(0));
        assertTrue(r.functions.contains("DATE_TRUNC"));
        assertTrue(r.identifiers.contains("created_at"));
        // dialect check：H2/PostgreSQL 支持；MySQL 不支持
        assertDoesNotThrow(() -> ExpressionMeasureValidator.checkDialectSupported(r, "H2", MT, MN));
        assertDoesNotThrow(() -> ExpressionMeasureValidator.checkDialectSupported(r, "PostgreSQL", MT, MN));
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.checkDialectSupported(r, "MySQL", MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_DIALECT_UNSUPPORTED.getErrorCode(), ex.getErrorCode());
    }

    /** JOIN 上下文限定名：{@code l.PRICE * r.QTY}（save-time 宽松 / JOIN 严格均通过）。 */
    @Test
    public void testJoinQualifiedExpressionSuccess() {
        // save-time 宽松
        ExpressionMeasureValidator.ValidatedExpression r1 =
                ExpressionMeasureValidator.validateStatic("l.PRICE * r.QTY",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        assertEquals("l.PRICE * r.QTY", r1.sqlFragment);
        assertTrue(r1.identifiers.contains("l.PRICE"));
        assertTrue(r1.identifiers.contains("r.QTY"));

        // JOIN 严格（带 left/right 列集合校验）
        Set<String> left = new HashSet<>(Arrays.asList("PRICE"));
        Set<String> right = new HashSet<>(Arrays.asList("QTY"));
        ExpressionMeasureValidator.ValidatedExpression r2 =
                ExpressionMeasureValidator.validateStatic("l.PRICE * r.QTY",
                        ExpressionMeasureValidator.ValidationOptions.joinStrict(left, right), MT, MN);
        assertEquals("l.PRICE * r.QTY", r2.sqlFragment);

        // JOIN 严格但列不在端点集合 → 失败
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("l.PRICE * r.QTY",
                        ExpressionMeasureValidator.ValidationOptions.joinStrict(
                                new HashSet<>(Arrays.asList("OTHER")), right), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
    }

    /** 单表上下文严格校验：裸列名 + 列集合校验。 */
    @Test
    public void testSingleTableStrictColumnSetCheck() {
        Set<String> cols = new HashSet<>(Arrays.asList("AMOUNT", "PRICE", "QTY"));
        ExpressionMeasureValidator.ValidatedExpression r =
                ExpressionMeasureValidator.validateStatic("AMOUNT * PRICE",
                        ExpressionMeasureValidator.ValidationOptions.singleTableStrict(cols), MT, MN);
        assertEquals("AMOUNT * PRICE", r.sqlFragment);

        // 列不在集合 → 失败
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("FOO * 1",
                        ExpressionMeasureValidator.ValidationOptions.singleTableStrict(cols), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());

        // 单表上下文禁限定名 → 失败
        NopException ex2 = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("l.AMOUNT * 1",
                        ExpressionMeasureValidator.ValidationOptions.singleTableStrict(cols), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex2.getErrorCode());
    }

    /** JOIN 上下文要求所有列限定：裸名拒绝。 */
    @Test
    public void testJoinContextRequiresQualifier() {
        Set<String> left = new HashSet<>(Arrays.asList("AMOUNT"));
        Set<String> right = new HashSet<>(Arrays.asList("QTY"));
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("AMOUNT * r.QTY",
                        ExpressionMeasureValidator.ValidationOptions.joinStrict(left, right), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
    }

    /** 字符串字面量内的关键字（'DROP'）经分词收集后不再触发误拒（safe-side 已修复为 safe-side 0 误拒）。 */
    @Test
    public void testStringLiteralContainingKeywordNotFalsePositive() {
        // 'DROP' 是字符串字面量，分词阶段已被收集为 ?，关键字扫描在剩余 token 上进行 → 不应触发误拒
        ExpressionMeasureValidator.ValidatedExpression r =
                ExpressionMeasureValidator.validateStatic("CASE WHEN status = 'DROP' THEN 1 ELSE 0 END",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        assertEquals("CASE WHEN status = ? THEN ? ELSE ? END", r.sqlFragment);
    }

    /** 标识符嵌入关键字（DROP_DATE 列名）不触发误拒（word-boundary）。 */
    @Test
    public void testIdentifierEmbeddingKeywordNotFalsePositive() {
        ExpressionMeasureValidator.ValidatedExpression r =
                ExpressionMeasureValidator.validateStatic("DROP_DATE * 2",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        assertEquals("DROP_DATE * ?", r.sqlFragment);
        assertTrue(r.identifiers.contains("DROP_DATE"));
    }

    // ============================================================
    // 失败路径：unparseable
    // ============================================================

    /** 未闭合括号 → unparseable。 */
    @Test
    public void testUnparseableUnclosedParen() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("(AMOUNT * 2",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode(), ex.getErrorCode());
    }

    /** 多余闭括号 → unparseable。 */
    @Test
    public void testUnparseableExtraCloseParen() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("AMOUNT * 2)",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode(), ex.getErrorCode());
    }

    /** 未闭合字符串字面量 → unparseable。 */
    @Test
    public void testUnparseableUnclosedString() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("status = 'unterminated",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode(), ex.getErrorCode());
    }

    /** 语句终止符 ';' → unparseable。 */
    @Test
    public void testUnparseableStatementTerminator() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("AMOUNT; DROP TABLE foo",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode(), ex.getErrorCode());
    }

    /** 行注释 '--' → unparseable。 */
    @Test
    public void testUnparseableLineComment() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("AMOUNT -- comment",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode(), ex.getErrorCode());
    }

    /** 块注释 '/*' → unparseable。 */
    @Test
    public void testUnparseableBlockComment() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("AMOUNT /* comment */",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode(), ex.getErrorCode());
    }

    // ============================================================
    // 失败路径：unsafe（关键字 / 函数 / 标识符黑名单）
    // ============================================================

    /** 含 DROP 关键字 → unsafe。 */
    @Test
    public void testUnsafeDropKeyword() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("DROP TABLE foo",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
    }

    /** 含 SLEEP 函数 → unsafe。 */
    @Test
    public void testUnsafeSleepFunction() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("SLEEP(5)",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
    }

    /** 含 PG_SLEEP 函数 → unsafe。 */
    @Test
    public void testUnsafePgSleepFunction() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("PG_SLEEP(5)",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
    }

    /** 含 BENCHMARK 函数 → unsafe。 */
    @Test
    public void testUnsafeBenchmarkFunction() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("BENCHMARK(1000000, MD5('x'))",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
    }

    /** 含 INSERT 关键字 → unsafe。 */
    @Test
    public void testUnsafeInsertKeyword() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("INSERT INTO t VALUES (1)",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
    }

    // ============================================================
    // R6.1（P2-13）：死条目修正回归（SET TRANSACTION / INTO OUTFILE / INTO DUMPFILE 拆分为单 token）
    // ============================================================

    /**
     * R6.1（P2-13）：{@code SET TRANSACTION} 类表达式 → unsafe，reason 命中目标集合 {SET, TRANSACTION}
     * 之一（scanBlacklist 按 token 流顺序首命中，SET 在前则 reason=SET，断言不钉死具体成员）。
     */
    @Test
    public void testR61SetTransactionBlocked() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("SET TRANSACTION ISOLATION LEVEL READ COMMITTED",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN),
                "SET TRANSACTION must be blocked (was a dead two-token entry)");
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
        String reason = String.valueOf(ex.getParam("reason"));
        assertTrue(reason.contains("SET") || reason.contains("TRANSACTION"),
                "reason must hit {SET, TRANSACTION}: " + reason);
    }

    /**
     * R6.1（P2-13）：独立 TRANSACTION 向量（不带 SET）唯一钉住 TRANSACTION 条目
     * （防 SET 先抛导致 TRANSACTION 从未被直接命中）。
     */
    @Test
    public void testR61TransactionAloneBlocked() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("TRANSACTION ISOLATION LEVEL READ COMMITTED",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN),
                "bare TRANSACTION must be blocked (pins the TRANSACTION entry itself)");
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
        String reason = String.valueOf(ex.getParam("reason"));
        assertTrue(reason.contains("TRANSACTION"),
                "reason must pin TRANSACTION: " + reason);
    }

    /**
     * R6.1（P2-13）：{@code x INTO OUTFILE '/tmp/f'} → unsafe，reason 命中集合 {INTO, OUTFILE, DUMPFILE}
     * 之一（INTO 加入后首命中 INTO；未加入则 OUTFILE 首命中——断言对两种落点均成立，对裁定中立）。
     */
    @Test
    public void testR61IntoOutfileBlocked() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("x INTO OUTFILE '/tmp/f'",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN),
                "INTO OUTFILE must be blocked (was a dead two-token function-list entry)");
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
        String reason = String.valueOf(ex.getParam("reason"));
        assertTrue(reason.contains("INTO") || reason.contains("OUTFILE") || reason.contains("DUMPFILE"),
                "reason must hit {INTO, OUTFILE, DUMPFILE}: " + reason);
    }

    /** R6.1（P2-13）负例：标识符嵌入 TRANSACTION（TRANSACTION_COUNT）不误伤（word-boundary）。 */
    @Test
    public void testR61IdentifierEmbeddingTransactionNotFalsePositive() {
        ExpressionMeasureValidator.ValidatedExpression r =
                ExpressionMeasureValidator.validateStatic("TRANSACTION_COUNT * 2",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        assertEquals("TRANSACTION_COUNT * ?", r.sqlFragment);
        assertTrue(r.identifiers.contains("TRANSACTION_COUNT"));
    }

    /** R6.1（P2-13）负例：字符串字面量内的 INTO OUTFILE 不误伤（分词阶段已收集为 ?）。 */
    @Test
    public void testR61StringLiteralIntoOutfileNotFalsePositive() {
        ExpressionMeasureValidator.ValidatedExpression r =
                ExpressionMeasureValidator.validateStatic("CASE WHEN status = 'INTO OUTFILE' THEN 1 ELSE 0 END",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        assertEquals("CASE WHEN status = ? THEN ? ELSE ? END", r.sqlFragment);
    }

    // ============================================================
    // P2-04（plan 2026-08-16-0226-1）：KEYWORD_BLACKLIST 函数形态豁免裁定钉死
    // ============================================================

    /**
     * <b>P2-04 裁定钉死（3 函数过）</b>：KEYWORD_BLACKLIST 中 REPLACE/TRUNCATE/INSERT 存在合法
     * callable 函数同形词（MySQL/Oracle 字符串/数值函数），其**函数调用形态**（FUNCTION_CALL token）
     * 是 SELECT 表达式合法用法——scanBlacklist 对 FUNCTION_CALL 只查 FUNCTION_BLACKLIST 是有意
     * 设计，非漏洞（表达式上下文无 DML/DDL 逃逸路径：聚合包裹、;}/注释已禁、参数化完整）。
     * 本测试钉死合法用法，防止未来"修复"把函数形态也拒掉（over-block 误伤）。
     */
    @Test
    public void testP204FunctionHomographsAllowed() {
        // REPLACE(str,from,to) 字符串替换
        ExpressionMeasureValidator.ValidatedExpression r1 =
                ExpressionMeasureValidator.validateStatic("REPLACE(name,'a','b')",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        assertTrue(r1.functions.contains("REPLACE"), "REPLACE function-call form is legal: " + r1.functions);

        // TRUNCATE(n,d) 数值截断
        ExpressionMeasureValidator.ValidatedExpression r2 =
                ExpressionMeasureValidator.validateStatic("TRUNCATE(1.23,1)",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        assertTrue(r2.functions.contains("TRUNCATE"), "TRUNCATE function-call form is legal: " + r2.functions);

        // INSERT(str,pos,len,newstr) MySQL 字符串函数
        ExpressionMeasureValidator.ValidatedExpression r3 =
                ExpressionMeasureValidator.validateStatic("INSERT(name,1,2,'x')",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        assertTrue(r3.functions.contains("INSERT"), "INSERT function-call form is legal: " + r3.functions);
    }

    /**
     * <b>P2-04 裁定钉死（3 语句拒）</b>：同形词的**语句形态**（REPLACE INTO / TRUNCATE TABLE /
     * INSERT INTO）token 类型为 IDENTIFIER，命中 KEYWORD_BLACKLIST 被拒——语句形态覆盖不因
     * 函数形态豁免而丢失。
     */
    @Test
    public void testP204StatementFormsStillRejected() {
        String[] statementForms = {
                "REPLACE INTO t VALUES (1)",
                "TRUNCATE TABLE t",
                "INSERT INTO t VALUES (1)"
        };
        for (String expr : statementForms) {
            NopException ex = assertThrows(NopException.class,
                    () -> ExpressionMeasureValidator.validateStatic(expr,
                            ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN),
                    "statement form must be rejected (keyword hit): " + expr);
            assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
            String reason = String.valueOf(ex.getParam("reason"));
            assertTrue(reason.contains("forbidden keyword"),
                    "reason must identify keyword hit: " + reason + " (expr=" + expr + ")");
        }
    }

    /**
     * <b>P2-04 裁定钉死（FUNCTION_BLACKLIST 拒 3）</b>：函数面真正的黑名单仍然生效——
     * SLEEP/BENCHMARK/LOAD_FILE 等副作用函数调用形态被拒（reason 标识 forbidden function）。
     */
    @Test
    public void testP204FunctionBlacklistStillRejected() {
        String[] functionPayloads = {
                "SLEEP(1)",
                "BENCHMARK(1000000, MD5('x'))",
                "LOAD_FILE('/etc/passwd')"
        };
        for (String expr : functionPayloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> ExpressionMeasureValidator.validateStatic(expr,
                            ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN),
                    "FUNCTION_BLACKLIST hit must be rejected: " + expr);
            assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
            String reason = String.valueOf(ex.getParam("reason"));
            assertTrue(reason.contains("forbidden function"),
                    "reason must identify function hit: " + reason + " (expr=" + expr + ")");
        }
    }

    // ============================================================
    // check2 P1（2026-08-23 审计）：H2/PG 文件访问函数族黑名单补齐
    // ============================================================

    /**
     * check2 P1 回归：H2 文件读写族函数（FILE_READ/FILE_WRITE/BACKUP/CSVWRITE/CSVREAD/RUNSCRIPT/
     * SCRIPT/SYS_EXEC）必须被 FUNCTION_BLACKLIST 拦截——修复前这些函数不在黑名单内，
     * {@code FILE_READ('/etc/passwd')} 型 expression 可通过校验并在 H2 数据源上执行本地文件读取
     * （与 MetaQualityRuleExecutor custom_sql 沙箱的文件族黑名单对齐）。
     */
    @Test
    public void testCheck2H2FileFamilyFunctionsBlocked() {
        String[] payloads = {
                "FILE_READ('/etc/passwd')",
                "FILE_WRITE('/tmp/x','data')",
                "BACKUP('/tmp/x.zip')",
                "CSVWRITE('/tmp/x.csv','SELECT 1 AS a')",
                "CSVREAD('/etc/passwd')",
                "RUNSCRIPT('/x.sql')",
                "SCRIPT('/x.sql')",
                "SYS_EXEC('id')"
        };
        for (String expr : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> ExpressionMeasureValidator.validateStatic(expr,
                            ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN),
                    "H2 file-family function must be rejected: " + expr);
            assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
            String reason = String.valueOf(ex.getParam("reason"));
            assertTrue(reason.contains("forbidden function"),
                    "reason must identify function hit: " + reason + " (expr=" + expr + ")");
        }
    }

    /** check2 P1 回归：PG 文件族函数（PG_READ_FILE/PG_READ_BINARY_FILE/PG_LS_DIR/PG_STAT_FILE 等）必须被拦截。 */
    @Test
    public void testCheck2PgFileFamilyFunctionsBlocked() {
        String[] payloads = {
                "PG_READ_FILE('/etc/passwd')",
                "PG_READ_BINARY_FILE('/etc/passwd')",
                "PG_LS_DIR('/')",
                "PG_LS_LOGDIR()",
                "PG_LS_WALDIR()",
                "PG_STAT_FILE('/etc/passwd')"
        };
        for (String expr : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> ExpressionMeasureValidator.validateStatic(expr,
                            ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN),
                    "PG file-family function must be rejected: " + expr);
            assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
            String reason = String.valueOf(ex.getParam("reason"));
            assertTrue(reason.contains("forbidden function"),
                    "reason must identify function hit: " + reason + " (expr=" + expr + ")");
        }
    }

    /**
     * check2 P1 附带死条目修正：黑名单匹配对 FUNCTION_CALL token 的大写形态，原小写条目
     * {@code xp_cmdshell} 永不命中——改为大写 {@code XP_CMDSHELL} 后小写输入（分词归一为大写）
     * 也必须被拦截。
     */
    @Test
    public void testCheck2XpCmdshellLowercaseInputBlocked() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("xp_cmdshell('id')",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN),
                "xp_cmdshell (lowercase input) must hit the uppercased XP_CMDSHELL entry");
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
        String reason = String.valueOf(ex.getParam("reason"));
        assertTrue(reason.contains("XP_CMDSHELL"), "reason must identify XP_CMDSHELL: " + reason);
    }

    /** check2 P1 负例：黑名单扩充不误伤常用合法聚合/字符串函数。 */
    @Test
    public void testCheck2LegitFunctionsStillAllowed() {
        String[] legit = {
                "ROUND(amount, 2)",
                "COALESCE(amount, 0)",
                "STDDEV_SAMP(amount)",
                "UPPER(status)"
        };
        for (String expr : legit) {
            assertDoesNotThrow(() -> ExpressionMeasureValidator.validateStatic(expr,
                    ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN),
                    "legit function must not be blocked: " + expr);
        }
    }

    // ============================================================
    // 失败路径：too-long（> 1000 字符）
    // ============================================================

    /** expression 文本超 1000 字符 → too-long（不截断、不静默存入）。 */
    @Test
    public void testTooLongExpressionFails() {
        // 构造 1001 字符的合法表达式（'A * 1 + A * 1 + ... ' 模式）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("AMOUNT * 1 + ");
        }
        sb.append("AMOUNT * 1");
        // 拼到至少 1001 字符
        while (sb.length() <= 1000) {
            sb.append(" + AMOUNT");
        }
        String tooLong = sb.toString();
        assertTrue(tooLong.length() > 1000, "test expression must exceed 1000 chars: " + tooLong.length());
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic(tooLong,
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_TOO_LONG.getErrorCode(), ex.getErrorCode());

        // checkCapacity 独立入口同样有效
        NopException ex2 = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.checkCapacity(tooLong, MT, MN));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXPRESSION_TOO_LONG.getErrorCode(), ex2.getErrorCode());
    }

    /** 恰好 1000 字符不报错（边界检查）。 */
    @Test
    public void testExactlyMaxLengthSucceeds() {
        // 构造恰好 1000 字符的合法表达式
        StringBuilder sb = new StringBuilder();
        sb.append("A");
        while (sb.length() < 1000) {
            sb.append(" + A");
        }
        // 调整到恰好 1000
        String expr = sb.toString();
        if (expr.length() > 1000) {
            expr = expr.substring(0, 1000);
            // 修整到合法结尾（避免截断 mid-token）
            int lastSpace = expr.lastIndexOf(' ');
            if (lastSpace > 0) {
                expr = expr.substring(0, lastSpace);
            }
        }
        // 边界检查不抛
        ExpressionMeasureValidator.checkCapacity(expr, MT, MN);
        // validateStatic 也不抛（可能因标识符 A 通过白名单）
        ExpressionMeasureValidator.ValidatedExpression r =
                ExpressionMeasureValidator.validateStatic(expr,
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        assertFalse(r.sqlFragment.isEmpty());
    }

    // ============================================================
    // 参数绑定正确性（字面量收集为 ?，标识符保留）
    // ============================================================

    /** 字面量按 SQL 出现顺序收集为参数；标识符原样保留。 */
    @Test
    public void testParameterBindingOrder() {
        // SUM(amount * 2) - 在 SELECT 阶段，2 是字面量
        ExpressionMeasureValidator.ValidatedExpression r =
                ExpressionMeasureValidator.validateStatic("amount * 2 + 3 * price - 'hello'",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        // 期望：amount ? + ? * price - ?（2, 3, 'hello'）
        assertEquals("amount * ? + ? * price - ?", r.sqlFragment);
        assertEquals(3, r.params.size());
        assertEquals(new java.math.BigDecimal("2"), r.params.get(0));
        assertEquals(new java.math.BigDecimal("3"), r.params.get(1));
        assertEquals("hello", r.params.get(2));
    }

    /** 小数 + 转义字符串字面量参数绑定。 */
    @Test
    public void testDecimalAndEscapedStringLiteralBinding() {
        // 'it''s' 是转义字符串字面量，值为 it's
        ExpressionMeasureValidator.ValidatedExpression r =
                ExpressionMeasureValidator.validateStatic("3.14 * col + 'it''s'",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN);
        assertEquals("? * col + ?", r.sqlFragment);
        assertEquals(2, r.params.size());
        assertEquals(new java.math.BigDecimal("3.14"), r.params.get(0));
        assertEquals("it's", r.params.get(1));
    }
}
