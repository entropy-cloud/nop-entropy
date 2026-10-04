/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.orm.eql.ast.SqlLimit;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7（roadmap EC ①）：SQL 标准分页两语法形态——{@code FETCH FIRST n ROWS ONLY} 与
 * {@code OFFSET n ROWS FETCH NEXT m ROWS ONLY}。
 *
 * <p>缺陷（修复前红）：两者均解析失败（`sqlLimit` 只有单一 {@code LIMIT expr [OFFSET expr]}
 * 备选）。修复后（裁定 = `ai-dev/design/eql-cross-db-compat/2026-10-04-wi7-phase0-adjudications.md`
 * §一/§二，候选 A）：解析期归一化装入**既有** {@link SqlLimit}{@code {limit, offset}}，
 * 不新增"语法形态"字段——输出形态由方言 {@code paginationHandler} 决定
 * （{@code IPaginationHandler.buildPageExpr} 无形态参数，{@code SqlLimit} 形态字段零输出侧消费点）。
 *
 * <p>守护面：既有 {@code LIMIT/OFFSET} 形态逐字不变（{@code TestEqlSelectAllLimitEcho}/
 * {@code TestEqlSelectAllLimitCompile} 全量回归 + 本类 LIMIT 守护组）；执行路径双族 lowering
 * 断言见 {@code TestEqlPaginationFetchCompile}。
 */
public class TestEqlPaginationFetchParse {

    static SqlQuerySelect parseSelect(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return (SqlQuerySelect) program.getStatements().get(0);
    }

    static String echo(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return program.toSqlString().replaceAll("\\s+", " ");
    }

    static String limitValue(SqlLimit limit) {
        return ((SqlNumberLiteral) limit.getLimit()).getValue();
    }

    static String offsetValue(SqlLimit limit) {
        return ((SqlNumberLiteral) limit.getOffset()).getValue();
    }

    // ---- 修复前红组：两形态可解析且归一化装入既有字段 ----

    @Test
    public void testFetchFirstParsesIntoLimit() {
        SqlQuerySelect select = parseSelect("select a.id from t a fetch first 5 rows only");
        SqlLimit limit = select.getLimit();
        assertNotNull(limit, "FETCH FIRST 形态必须解析出 SqlLimit");
        assertEquals("5", limitValue(limit));
        assertNull(limit.getOffset(), "FETCH FIRST 无 offset ⇒ offset=null");
    }

    @Test
    public void testOffsetFetchNextParsesIntoOffsetAndLimit() {
        SqlQuerySelect select = parseSelect("select a.id from t a offset 3 rows fetch next 10 rows only");
        SqlLimit limit = select.getLimit();
        assertNotNull(limit, "OFFSET..FETCH NEXT 形态必须解析出 SqlLimit");
        assertEquals("10", limitValue(limit), "FETCH NEXT m ⇒ limit=m");
        assertEquals("3", offsetValue(limit), "OFFSET n ROWS ⇒ offset=n");
    }

    @Test
    public void testFetchFirstFormEquivalentToLimitForm() {
        // 一致性用例（约束 4）：两形态归一化后 AST 等价
        SqlLimit fetch = parseSelect("select a.id from t a fetch first 5 rows only").getLimit();
        SqlLimit limit = parseSelect("select a.id from t a limit 5").getLimit();
        assertEquals(limitValue(limit), limitValue(fetch));
        assertNull(fetch.getOffset());
        assertNull(limit.getOffset());
    }

    @Test
    public void testOffsetFetchFormEquivalentToLimitOffsetForm() {
        SqlLimit fetch = parseSelect("select a.id from t a offset 3 rows fetch next 10 rows only").getLimit();
        SqlLimit limit = parseSelect("select a.id from t a limit 10 offset 3").getLimit();
        assertEquals(limitValue(limit), limitValue(fetch));
        assertEquals(offsetValue(limit), offsetValue(fetch));
    }

    @Test
    public void testFetchFirstParameterMarkerParses() {
        SqlLimit limit = parseSelect("select a.id from t a fetch first ? rows only").getLimit();
        assertNotNull(limit, "参数 marker 形态必须解析");
        assertNotNull(limit.getLimit());
        assertNull(limit.getOffset());
    }

    @Test
    public void testOffsetFetchParameterMarkerParses() {
        SqlLimit limit = parseSelect("select a.id from t a offset ? rows fetch next ? rows only").getLimit();
        assertNotNull(limit);
        assertNotNull(limit.getLimit());
        assertNotNull(limit.getOffset());
    }

    // ---- 回显规范化（Phase 0 裁定：规范化 `limit x [offset y]`，不做源文本保真） ----

    @Test
    public void testFetchFirstEchoesNormalizedLimitForm() {
        String out = echo("select a.id from t a fetch first 5 rows only");
        assertTrue(out.contains("limit 5"), out);
        assertFalse(out.contains("fetch"), out);
    }

    @Test
    public void testOffsetFetchEchoesNormalizedLimitOffsetForm() {
        String out = echo("select a.id from t a offset 3 rows fetch next 10 rows only");
        assertTrue(out.contains("limit 10"), out);
        assertTrue(out.contains("offset 3"), out);
        assertFalse(out.contains("fetch"), out);
    }

    @Test
    public void testEchoRoundTripsFetchFirst() {
        String echoed = echo("select a.id from t a fetch first 5 rows only");
        SqlLimit roundTripped = parseSelect(echoed).getLimit();
        assertNotNull(roundTripped, "回显文本必须可重解析: " + echoed);
        assertEquals("5", limitValue(roundTripped));
        assertNull(roundTripped.getOffset());
    }

    @Test
    public void testEchoRoundTripsOffsetFetch() {
        String echoed = echo("select a.id from t a offset 3 rows fetch next 10 rows only");
        SqlLimit roundTripped = parseSelect(echoed).getLimit();
        assertNotNull(roundTripped, "回显文本必须可重解析: " + echoed);
        assertEquals("10", limitValue(roundTripped));
        assertEquals("3", offsetValue(roundTripped));
    }

    // ---- 既有 LIMIT 形态守护组（逐字不变） ----

    @Test
    public void testLimitFormUnchanged() {
        SqlLimit limit = parseSelect("select a.id from t a limit 10 offset 5").getLimit();
        assertEquals("10", limitValue(limit));
        assertEquals("5", offsetValue(limit));

        SqlLimit limitOnly = parseSelect("select a.id from t a limit 3").getLimit();
        assertEquals("3", limitValue(limitOnly));
        assertNull(limitOnly.getOffset());
    }

    @Test
    public void testLimitFormEchoUnchanged() {
        String out = echo("select a.id from t a limit 10 offset 5");
        assertTrue(out.contains("limit 10"), out);
        assertTrue(out.contains("offset 5"), out);
        assertInstanceOf(SqlNumberLiteral.class, parseSelect("select a.id from t a limit 3").getLimit().getLimit());
    }
}
