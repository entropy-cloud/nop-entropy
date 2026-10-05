/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.SqlProgram;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 BUG-10：`cross join` 静默误解析。
 *
 * <p>缺陷（修复前）：`joinType_` 只含 inner/left/right/full，`CROSS` 在
 * {@code unreservedWord_} 中 ⇒ `from a cross join b` 把 `cross` 消费为别名，
 * 回显产出 `from a as cross join b`（静默损坏）。
 *
 * <p>修复后：文法新增 `crossJoin_: CROSS JOIN` 并入 `joinType_`，`SqlJoinType`
 * 新增 CROSS_JOIN，生成器经既有 `getJoinType().getText()` 输出 `cross join`。
 * `CROSS` 不移出 `unreservedWord_` 与 7 个方言 reservedKeywords（roadmap 明令）；
 * `natural join` 不在授权范围（merge 后按 Plan 2307 V9 死 token 移除口径降级为别名，见 testNaturalJoinNotAuthorized）。
 */
public class TestEqlCrossJoinParse extends BaseTestCase {

    static String echo(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return program.toSqlString().replaceAll("\\s+", " ");
    }

    @Test
    public void testCrossJoinParsesAndEchoes() {
        // 修复前红：`cross` 被消费为别名，回显 `from a as cross join b`
        String out = echo("select count(*) from a cross join b");
        assertTrue(out.contains("from a cross join b"), out);
    }

    @Test
    public void testCrossJoinWithEntitySyntax() {
        String out = echo("select o.id from AppUser o cross join AppRole r");
        assertTrue(out.contains("cross join"), out);
    }

    @Test
    public void testLegacyJoinFormsUnchanged() {
        // 既有 join 形态回归：解析与输出不变。既有回显行为（BUG-4 族回显小节，WI2 范围）：
        // INNER 可选 ⇒ inner join 回显为 `join`；LEFT OUTER? JOIN 的 OUTER 在回显中丢弃
        // ⇒ `left join`。本测试锁定的是"修复前后这些回显不变"。
        assertTrue(echo("select 1 from a inner join b on a.id = b.id").contains(" join b "));
        assertTrue(echo("select 1 from a left join b on a.id = b.id").contains("left join"));
        assertTrue(echo("select 1 from a left outer join b on a.id = b.id").contains("left join"));
        assertTrue(echo("select 1 from a right join b on a.id = b.id").contains("right join"));
        assertTrue(echo("select 1 from a full join b on a.id = b.id").contains("full join"));
    }

    @Test
    public void testNaturalJoinNotAuthorized() {
        // 【merge note / Plan 2307 V9】master 移除死 token NATURAL/USING（全仓 g4 零规则
        // 引用；EQL 以普通 JOIN + ON 表达），`natural` 现按普通标识符进入别名路径——
        // ours 原断言"解析期响亮失败"随之退役。natural join 语义仍未获授权：
        // 实体表形态 `from AppDept d natural join AppRole r` 在 transform 层被
        // ERR_EQL_JOIN_NO_CONDITION 响亮拒绝（普通 join 漏 ON 校验对别名路径同样生效）；
        // 本断言钉住"natural 不再是关键字、按别名解析"的新契约。
        String out = echo("select 1 from a natural join b");
        assertTrue(out.contains("a as natural join b"), out);
    }

    @Test
    public void testCrossAsAliasStillWorks() {
        // `cross` 仍在 unreservedWord_：无 JOIN 关键字时继续按别名解析（裁定记录：
        // `from a cross` 中 cross 作别名的用法不受新文法影响——joinType_ 要求
        // CROSS 后必须跟 JOIN，不与别名产生歧义）
        String out = echo("select 1 from a cross");
        assertTrue(out.contains("as cross"), out);
    }
}
