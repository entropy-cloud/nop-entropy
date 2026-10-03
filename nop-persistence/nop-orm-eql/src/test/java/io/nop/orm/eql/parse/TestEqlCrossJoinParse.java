/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.SqlProgram;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
 * `natural join` 不在授权范围，保持响亮失败。
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
    public void testNaturalJoinStillFailsLoudly() {
        // natural join 未获 roadmap 授权，保持解析期响亮失败（行为不变）
        try {
            echo("select 1 from a natural join b");
            fail("natural join must keep failing at parse time");
        } catch (NopException e) {
            assertTrue(e.getErrorCode().startsWith("nop.err.antlr"),
                    "expected antlr parse error, got: " + e.getErrorCode());
        }
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
