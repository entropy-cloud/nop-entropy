/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.SqlProgram;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 BUG-6：`full outer join` 解析失败。
 *
 * <p>缺陷（修复前）：文法 {@code fullJoin_: FULL JOIN} 不带 {@code OUTER?}（同块
 * left/right join 都带）⇒ `full join` 可解析、`full outer join` 解析失败。
 *
 * <p>修复后：`fullJoin_: FULL OUTER? JOIN` 与 left/right 形态对齐（OUTER/FULL 均为
 * 关键字 token、不在 unreservedWord_，无 WI1 cross join 式别名歧义）。能力门
 * （`supportFullJoin` 编译期消费）见 {@code TestEqlFullJoinDialectGate}。
 */
public class TestEqlFullJoinParse extends BaseTestCase {

    static String echo(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        return program.toSqlString().replaceAll("\\s+", " ");
    }

    @Test
    public void testFullOuterJoinParses() {
        // 修复前红：`full outer join` 解析失败（nop.err.antlr.common.parse-fail）
        String out = echo("select 1 from a full outer join b on a.id = b.id");
        assertTrue(out.contains("full join"), out);
    }

    @Test
    public void testFullOuterJoinEntitySyntax() {
        String out = echo("select o.id from AppUser o full outer join AppRole r on o.id = r.id");
        assertTrue(out.contains("full join"), out);
    }

    @Test
    public void testFullJoinWithoutOuterUnchanged() {
        // 既有行为守护：`full join`（无 OUTER）修复前后行为不变
        String out = echo("select 1 from a full join b on a.id = b.id");
        assertTrue(out.contains("full join"), out);
    }

    @Test
    public void testNaturalJoinNotAuthorized() {
        // 【merge note / Plan 2307 V9】master 移除死 token NATURAL/USING（全仓 g4 零规则
        // 引用；EQL 以普通 JOIN + ON 表达），`natural` 现按普通标识符进入别名路径——
        // ours 原断言"解析期响亮失败"随之退役。`a natural full outer join b` 解析为
        // 「别名 natural + full outer join b」（OUTER 回显丢弃为既有行为），
        // natural join 语义仍未获授权；本断言钉住该降级形态，
        // 防未来文法变更无意引入 NATURAL 语法糖。
        String out = echo("select 1 from a natural full outer join b");
        assertTrue(out.contains("as natural full join b"), out);
    }
}
