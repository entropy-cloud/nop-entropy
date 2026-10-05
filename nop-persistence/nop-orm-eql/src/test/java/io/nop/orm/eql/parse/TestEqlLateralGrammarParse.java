/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlSubqueryTableSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2307 Phase 2 / V1：<code>LATERAL</code> 的词法与 AST 解析面。
 *
 * <p>覆盖两件事：
 * <ol>
 *   <li><code>LATERAL?</code> 前缀被正确记录到 <code>SqlSubqueryTableSource.lateral</code>；</li>
 *   <li><code>lateral</code> 登记为非保留字后可作列名/别名（用户可见的词法放宽）。</li>
 * </ol>
 */
public class TestEqlLateralGrammarParse {

    static SqlProgram parse(String sql) {
        return new EqlASTParser().parseFromText(null, sql);
    }

    // ---- lateral 前缀进入 AST ----

    @Test
    public void testLateralFlagSetOnSubqueryTableSource() {
        SqlProgram program = parse("select 1 from a, lateral (select 1 from b) t");
        SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
        SqlSubqueryTableSource src = (SqlSubqueryTableSource) select.getFrom().getTableSources().get(1);
        assertTrue(src.getLateral(), "lateral 前缀必须被记录到 AST");
    }

    @Test
    public void testLateralFlagAbsentByDefault() {
        SqlProgram program = parse("select 1 from a, (select 1 from b) t");
        SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
        SqlSubqueryTableSource src = (SqlSubqueryTableSource) select.getFrom().getTableSources().get(1);
        assertFalse(src.getLateral(), "未写 lateral 时标志必须为 false");
    }

    @Test
    public void testLateralCaseInsensitive() {
        for (String kw : new String[]{"lateral", "LATERAL", "LaTeRaL"}) {
            SqlProgram program = parse("select 1 from a, " + kw + " (select 1 from b) t");
            SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
            SqlSubqueryTableSource src =
                    (SqlSubqueryTableSource) select.getFrom().getTableSources().get(1);
            assertTrue(src.getLateral(), kw + " 必须被识别为 lateral");
        }
    }

    // ---- lateral 作为标识符（词法放宽） ----

    @Test
    public void testLateralUsableAsColumnName() {
        SqlProgram program = parse("select lateral from t");
        SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
        SqlExprProjection proj = (SqlExprProjection) select.getProjections().get(0);
        assertEquals("lateral", ((SqlColumnName) proj.getExpr()).getName());
    }

    @Test
    public void testLateralUsableAsAlias() {
        SqlProgram program = parse("select a as lateral from t");
        SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
        SqlExprProjection proj = (SqlExprProjection) select.getProjections().get(0);
        assertNotNull(proj.getAlias());
        assertEquals("lateral", proj.getAlias().getAlias());
    }

    @Test
    public void testLateralUsableAsTableAlias() {
        SqlProgram program = parse("select 1 from t lateral");
        SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
        assertNotNull(select.getFrom().getTableSources());
    }

    @Test
    public void testLateralStillUsableAsJoinKeywordPosition() {
        // 词法放宽不能破坏 LATERAL 关键字本身的识别：显式 lateral 前缀仍然生效
        SqlProgram program = parse("select 1 from a, lateral (select 1 from b) t");
        assertNotNull(program);
    }
}