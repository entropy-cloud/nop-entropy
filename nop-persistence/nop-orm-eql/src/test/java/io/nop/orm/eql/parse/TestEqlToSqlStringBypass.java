/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.SqlProgram;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Plan 2307 Phase 0: {@code EqlASTNode.toSqlString()} 旁路的基线守护测试。
 *
 * <p>{@code toSqlString()} 直接在<strong>未经 EqlTransformVisitor 变换</strong>的原始 AST 上运行
 * {@code AstToEqlGenerator}，是一条绕过语义分析的调试/日志旁路。2307 的多个修复会给 printer
 * 加 fail-fast（遇到无 printer 的节点时抛错而非静默丢字段），这类改动一旦误伤本旁路，
 * 就会让调试路径在打印任意"半成品"AST 时崩溃。
 *
 * <p>因此本类把当前 live baseline 的输出形态逐条钉死。后续 Phase 修改 printer 时，
 * 本类必须保持全绿——除非某条 baseline 因<strong>该 Phase 的目标修复本身</strong>
 * 而必须改变（此时应在对应 Phase 的测试中断言新形态，并在此处同步更新期望值）。
 *
 * <p>注意：本类断言的是「原始 AST 直接打印」的形态，与编译通道
 * （{@code EqlCompiler -> AstToSqlGenerator}）的输出形态不同——编译通道会先做
 * 语义分析、列名解析、方言翻译。
 */
public class TestEqlToSqlStringBypass extends BaseTestCase {

    /**
     * 归一化打印结果：压掉换行与多余空白，只保留 token 序列的形状。
     * printer 的缩进/换行属于排版细节，不是本旁路的契约。
     */
    static String bypass(String eql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, eql);
        assertNotNull(program, "parse must succeed: " + eql);
        return program.toSqlString().replaceAll("\\s+", " ").trim();
    }

    // ---- 普通语句：旁路必须能完整打印 ----

    @Test
    public void testSimpleSelectRoundTrip() {
        assertEquals("select a from t", bypass("select a from t"));
    }

    @Test
    public void testSubqueryTableSourceRoundTrip() {
        // 子查询表源：括号 + 别名。这是 LATERAL 修复(Phase 2)的邻接形态，
        // 修复后「非 lateral 子查询」的输出必须与本 baseline 完全一致。
        assertEquals("select 1 from a, ( select 1 from b ) as t",
                bypass("select 1 from a, (select 1 from b) t"));
    }

    @Test
    public void testLateralSubqueryTableSourceRoundTrip() {
        // Phase 2 / V1 修复后：lateral 前缀必须出现在打印结果里。
        // 修复前该断言的 baseline 是 "select 1 from a, ( select 1 from b ) as t"（lateral 丢失）。
        assertEquals("select 1 from a, lateral ( select 1 from b ) as t",
                bypass("select 1 from a, lateral (select 1 from b) t"));
    }

    @Test
    public void testOrderByItemRoundTrip() {
        // ORDER BY 项是 nullsFirst/collate 修复(Phase 5)的邻接形态。
        // 注：printer 在 asc/desc 后带尾随空格，故逗号前有一个空格——这是既有排版行为。
        assertEquals("select a from t order by a asc , b desc",
                bypass("select a from t order by a, b desc"));
    }

    @Test
    public void testJoinTableSourceRoundTrip() {
        // join 类型串是 CROSS JOIN 修复(Phase 3)的邻接形态。
        // 注：printer 输出 joinType 的原始文本，`JOIN` 不带 `INNER` 前缀。
        assertEquals("select a from a join b on a.id = b.id",
                bypass("select a from a join b on a.id = b.id"));
    }

    // ---- printer fail-fast 不得误伤本旁路 ----

    @Test
    public void testNonTransformedAstIsPrintable() {
        // 未经 transform 的 AST 里列名仍是裸标识符、别名未规范化。
        // printer 允许直接打印这种 AST 是本旁路存在的意义；
        // 任何"AST 未变换则抛错"的 fail-fast 都会破坏它。
        String text = bypass("select t.a as x, t.b from t where t.c > 1");
        assertEquals("select t.a as x, t.b from t where t.c > 1", text);
    }

    @Test
    public void testUnsupportedDialectSpecificNodeStillPrintable() {
        // TUMBLE 伪表函数在 SQL 通道不可执行，但 EQL 通道必须能打印
        // （见 AstToEqlGenerator#visitSqlTumbleTableSource 的 documented boundary）。
        String text = bypass("select a from TUMBLE(orders.ts, INTERVAL 5 SECOND) o");
        assertEquals("select a from TUMBLE(orders.ts, INTERVAL 5 SECOND) as o", text);
    }
}