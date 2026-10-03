/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
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
    public void testNaturalJoinStillFailsLoudly() {
        // natural join 未获 roadmap 授权，保持解析期响亮失败（行为不变）
        try {
            echo("select 1 from a natural full outer join b");
            fail("natural full outer join must keep failing at parse time");
        } catch (NopException e) {
            assertTrue(e.getErrorCode().startsWith("nop.err.antlr"),
                    "expected antlr parse error, got: " + e.getErrorCode());
        }
    }
}
