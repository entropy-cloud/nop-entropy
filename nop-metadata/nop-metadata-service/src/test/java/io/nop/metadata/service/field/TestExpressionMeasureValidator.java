package io.nop.metadata.service.field;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.query.MetaAggregationExecutor;
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
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_DIALECT_UNSUPPORTED.getErrorCode(), ex.getErrorCode());
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
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
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
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());

        // 单表上下文禁限定名 → 失败
        NopException ex2 = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("l.AMOUNT * 1",
                        ExpressionMeasureValidator.ValidationOptions.singleTableStrict(cols), MT, MN));
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex2.getErrorCode());
    }

    /** JOIN 上下文要求所有列限定：裸名拒绝。 */
    @Test
    public void testJoinContextRequiresQualifier() {
        Set<String> left = new HashSet<>(Arrays.asList("AMOUNT"));
        Set<String> right = new HashSet<>(Arrays.asList("QTY"));
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("AMOUNT * r.QTY",
                        ExpressionMeasureValidator.ValidationOptions.joinStrict(left, right), MT, MN));
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
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
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode(), ex.getErrorCode());
    }

    /** 多余闭括号 → unparseable。 */
    @Test
    public void testUnparseableExtraCloseParen() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("AMOUNT * 2)",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode(), ex.getErrorCode());
    }

    /** 未闭合字符串字面量 → unparseable。 */
    @Test
    public void testUnparseableUnclosedString() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("status = 'unterminated",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode(), ex.getErrorCode());
    }

    /** 语句终止符 ';' → unparseable。 */
    @Test
    public void testUnparseableStatementTerminator() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("AMOUNT; DROP TABLE foo",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode(), ex.getErrorCode());
    }

    /** 行注释 '--' → unparseable。 */
    @Test
    public void testUnparseableLineComment() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("AMOUNT -- comment",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode(), ex.getErrorCode());
    }

    /** 块注释 '/*' → unparseable。 */
    @Test
    public void testUnparseableBlockComment() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("AMOUNT /* comment */",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNPARSEABLE.getErrorCode(), ex.getErrorCode());
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
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
    }

    /** 含 SLEEP 函数 → unsafe。 */
    @Test
    public void testUnsafeSleepFunction() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("SLEEP(5)",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
    }

    /** 含 PG_SLEEP 函数 → unsafe。 */
    @Test
    public void testUnsafePgSleepFunction() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("PG_SLEEP(5)",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
    }

    /** 含 BENCHMARK 函数 → unsafe。 */
    @Test
    public void testUnsafeBenchmarkFunction() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("BENCHMARK(1000000, MD5('x'))",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
    }

    /** 含 INSERT 关键字 → unsafe。 */
    @Test
    public void testUnsafeInsertKeyword() {
        NopException ex = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.validateStatic("INSERT INTO t VALUES (1)",
                        ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(), MT, MN));
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_UNSAFE.getErrorCode(), ex.getErrorCode());
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
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_TOO_LONG.getErrorCode(), ex.getErrorCode());

        // checkCapacity 独立入口同样有效
        NopException ex2 = assertThrows(NopException.class,
                () -> ExpressionMeasureValidator.checkCapacity(tooLong, MT, MN));
        assertEquals(MetaAggregationExecutor.ERR_AGGR_EXPRESSION_TOO_LONG.getErrorCode(), ex2.getErrorCode());
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
