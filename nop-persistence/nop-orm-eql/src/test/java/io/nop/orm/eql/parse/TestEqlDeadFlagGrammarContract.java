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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10b（Phase 0 §2.2/§2.4 终裁：档 2 契约登记）：`supportUpdateFromJoin`/
 * `supportDeleteFromJoin` 的文法级契约——EQL 当前**没有** UPDATE..FROM 与多表 DELETE
 * 语法，这些写法在解析层**响亮失败**（不是静默丢子句）。本测试锁定该契约：
 * 未来文法 WI（plan-first，路径 ⓒ）扩展语法时若静默退化（解析通过但丢弃 FROM/USING），
 * 此处即红——与 BUG-1 同型的静默丢弃被前置拦截。
 *
 * <p>两标志的接线触发条件（xdef javadoc 已注记）：对应文法 WI 立项时必须在
 * `EqlTransformVisitor` 落编译期能力门并消费标志。
 */
public class TestEqlDeadFlagGrammarContract extends BaseTestCase {

    static void parse(String sql) {
        new EqlASTParser().parseFromText(null, sql);
    }

    /**
     * UPDATE..FROM（MySQL/MSSQL 多表更新形态）：解析失败 = 响亮，非静默丢 FROM
     * （实测报 `nop.err.antlr.not-end-properly`，offendingToken=from）。
     */
    @Test
    public void testUpdateFromClauseIsLoudParseError() {
        NopException e = assertThrows(NopException.class,
                () -> parse("update AppDept set name = 'x' from AppRole r where AppDept.id = r.id"),
                "UPDATE..FROM must not parse (grammar absent - loud failure is the contract)");
        assertEquals("nop.err.antlr.not-end-properly", e.getErrorCode(), "offending token must be the FROM clause");
    }

    /**
     * 多表 DELETE（`DELETE t1, t2 FROM …`，MySQL 形态）：解析失败 = 响亮。
     */
    @Test
    public void testMultiTableDeleteIsLoudParseError() {
        assertThrows(NopException.class,
                () -> parse("delete from AppDept, AppRole where AppDept.id = AppRole.id"),
                "multi-table DELETE must not parse");
    }

    /**
     * DELETE..USING（PostgreSQL 多表删除形态）：解析失败 = 响亮。
     */
    @Test
    public void testDeleteUsingIsLoudParseError() {
        assertThrows(NopException.class,
                () -> parse("delete from AppDept using AppRole where AppDept.id = AppRole.id"),
                "DELETE..USING must not parse");
    }

    /**
     * 契约边界守护：单表 UPDATE / 单表 DELETE 别名形态（两标志今天的实际覆盖面，
     * 行为契约登记见 `TestEqlTableAliasSuppressionContract`）照常解析。
     */
    @Test
    public void testSingleTableAliasFormsStillParse() {
        SqlProgram update = new EqlASTParser().parseFromText(null,
                "update AppDept o set o.name = 'x' where o.id = 1");
        assertTrue(update.toSqlString() != null && !update.toSqlString().isEmpty());

        SqlProgram delete = new EqlASTParser().parseFromText(null,
                "delete from AppDept o where o.id = 1");
        assertTrue(delete.toSqlString() != null && !delete.toSqlString().isEmpty());
    }
}
