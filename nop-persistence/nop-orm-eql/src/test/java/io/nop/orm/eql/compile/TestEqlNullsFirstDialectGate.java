/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.orm.eql.ast.SqlOrderByItem;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlStatement;
import io.nop.orm.eql.compile.TestEqlCastTypeAliasCompile.TestCompileContext;
import io.nop.orm.eql.parse.EqlASTParser;
import io.nop.orm.eql.sql.AstToSqlGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7（roadmap EC ②/③/③b）：{@code NULLS FIRST/LAST} 的方言门与 {@code defaultNullsFirst}
 * 默认序消费——三分形态的行为矩阵（裁定 =
 * {@code ai-dev/design/eql-cross-db-compat/2026-10-04-wi7-phase0-adjudications.md} §三）。
 *
 * <p>本 Phase（Phase 2）落地时**尚无文法赋值路径**（Phase 3 才激活）：{@code nullsFirst}
 * 以程序化 AST 构造（parse 文本 → {@code SqlOrderByItem.setNullsFirst(...)} → transform →
 * generate，复刻 {@code EqlCompiler} 管线核心），端到端文法激活用例见
 * {@code TestEqlNullsFirstSyntax}（Phase 3）。
 *
 * <p>行为矩阵：
 * <ul>
 * <li>分支 1（{@code isSupportNullsFirst()=true}，duckdb/h2/oracle/postgresql）：请求即打印；</li>
 * <li>分支 0（{@code nullsFirst == null}）：输出与未注入时逐字节一致（BC 硬约束，
 * 缺省不补子句）；</li>
 * <li>分支 2（false + 显式请求）：缺省序推导**仅一手文档已证方言集**（mysql 系/mssql/h2）
 * 允许"一致省略"，不一致或集合外（dm/tdengine/db2 无一手文档，红线 [未取证]）一律编译期
 * 报 {@code ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE}（feature=nullsFirst(first|last)）。</li>
 * </ul>
 * 8 方言 {@code defaultNullsFirst} 消费路径断言（EC ③）：mysql/mssql 走省略/报错推导；
 * duckdb/postgresql/oracle/h2 走 support=true 打印分支（defaultNullsFirst 结构性不可达——
 * 打印严格优于查缺省）；dm/tdengine/db2 走"集合外一律报错"（其 defaultNullsFirst 配置
 * 按红线不作等价性主张）。flag 翻转因果用手构方言证明（同名 mysql 翻 flag 行为翻转）。
 */
public class TestEqlNullsFirstDialectGate {

    static final String EQL_ASC = "select o.name from AppDept o order by o.id";
    static final String EQL_DESC = "select o.name from AppDept o order by o.id desc";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static IDialect dialectOf(String name) {
        IDialect dialect = DialectManager.instance().getDialect(name);
        assertNotNull(dialect, name + " dialect must be loadable in nop-orm-eql test env");
        return dialect;
    }

    /**
     * 手构方言：name 经 resourcePath 派生（{@code DialectImpl.getName()} 读模型 location
     * path 的 fileFullName），须指向已证方言集成员名才能进入缺省序推导分支。
     */
    static IDialect handDialect(String name, Boolean defaultNullsFirst, Boolean supportNullsFirst) {
        DialectModel model = new DialectModel();
        model.setLocation(SourceLocation.fromPath("/nop/dao/dialect/" + name + ".dialect.xml"));
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        io.nop.dao.dialect.model.DialectFeatures features = new io.nop.dao.dialect.model.DialectFeatures();
        if (supportNullsFirst != null)
            features.setSupportNullsFirst(supportNullsFirst);
        model.setFeatures(features);
        if (defaultNullsFirst != null)
            model.setDefaultNullsFirst(defaultNullsFirst);
        // DialectImpl 构造期解析 current_timestamp 渲染 SQL，必须注册（既有手构先例同款）
        io.nop.dao.dialect.model.SqlNativeFunctionModel currentTimestamp = new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        model.setFunctions(Collections.singletonList(currentTimestamp));
        return new DialectImpl(model);
    }

    /**
     * 程序化 nullsFirst 注入 + 完整 compile 管线（parse → setNullsFirst → transform →
     * generate），复刻 {@code EqlCompiler.compile} 核心（Phase 2 无文法赋值路径的替代载体）。
     */
    static String sqlOf(IDialect dialect, String eql, Boolean nullsFirst) {
        SqlProgram program = new EqlASTParser().parseFromText(null, eql);
        SqlStatement stm = program.getStatements().get(0);
        if (nullsFirst != null) {
            SqlQuerySelect select = (SqlQuerySelect) stm;
            SqlOrderByItem item = select.getOrderBy().getItems().get(0);
            item.setNullsFirst(nullsFirst);
        }
        TestCompileContext ctx = TestEqlCastTypeAliasCompile.contextOf(dialect);
        io.nop.orm.eql.compile.EqlTransformVisitor transformer = new io.nop.orm.eql.compile.EqlTransformVisitor(
                ctx, "test");
        transformer.transform(program);
        AstToSqlGenerator gen = new AstToSqlGenerator(transformer.getDialect(), false);
        gen.setPretty(true);
        gen.visit(stm);
        return gen.getSql().getText().replaceAll("\\s+", " ");
    }

    static NopException featureError(IDialect dialect, String eql, boolean nullsFirst) {
        return assertThrows(NopException.class, () -> sqlOf(dialect, eql, nullsFirst));
    }

    // ---- 分支 1：supportNullsFirst=true（真实方言 4 个）请求即打印 ----

    @Test
    public void testOraclePrintsRequestedNullsOrder() {
        assertTrue(sqlOf(dialectOf("oracle"), EQL_ASC, true).contains("nulls first"));
        assertTrue(sqlOf(dialectOf("oracle"), EQL_ASC, false).contains("nulls last"));
        assertTrue(sqlOf(dialectOf("oracle"), EQL_DESC, true).contains("nulls first"));
        assertTrue(sqlOf(dialectOf("oracle"), EQL_DESC, false).contains("nulls last"));
    }

    @Test
    public void testPostgresqlPrintsRequestedNullsOrder() {
        assertTrue(sqlOf(dialectOf("postgresql"), EQL_ASC, true).contains("nulls first"));
        assertTrue(sqlOf(dialectOf("postgresql"), EQL_DESC, false).contains("nulls last"));
    }

    @Test
    public void testDuckdbPrintsRequestedNullsOrder() {
        assertTrue(sqlOf(dialectOf("duckdb"), EQL_ASC, false).contains("nulls last"));
        assertTrue(sqlOf(dialectOf("duckdb"), EQL_DESC, false).contains("nulls last"));
    }

    @Test
    public void testH2PrintsRequestedNullsOrder() {
        assertTrue(sqlOf(dialectOf("h2"), EQL_ASC, true).contains("nulls first"));
        assertTrue(sqlOf(dialectOf("h2"), EQL_DESC, false).contains("nulls last"));
    }

    // ---- 分支 0：nullsFirst == null 逐字节不变（BC 硬约束，代表方言） ----

    @Test
    public void testNullUnsetOutputByteIdenticalOnMysqlAndOracle() {
        for (String name : new String[]{"mysql", "oracle", "tdengine", "db2"}) {
            IDialect dialect = dialectOf(name);
            String baselineAsc = sqlOf(dialect, EQL_ASC, null);
            assertFalse(baselineAsc.contains("nulls"), name + ": " + baselineAsc);
            // 门控分支 0 = 委托 super ⇒ 与未经过门控的输出逐字节一致（同输入两次生成恒等）
            assertEquals(baselineAsc, sqlOf(dialect, EQL_ASC, null), name);
        }
    }

    // ---- 分支 2：mysql/mssql（defaultNullsFirst=true + 一手文档）省略/报错三态（EC ④） ----

    @Test
    public void testMysqlConsistentRequestOmittedConflictThrows() {
        IDialect mysql = dialectOf("mysql");
        // asc nulls first = 缺省序一致 ⇒ 省略（与未注入输出逐字节一致）
        String baseline = sqlOf(mysql, EQL_ASC, null);
        assertEquals(baseline, sqlOf(mysql, EQL_ASC, true), "mysql asc nulls first == default ⇒ omit");
        // asc nulls last 与缺省冲突 ⇒ 编译期响亮报错
        NopException e = featureError(mysql, EQL_ASC, false);
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("nullsFirst(last)", e.getParam("feature"));
        // desc nulls last = DESC 缺省序（镜像）一致 ⇒ 省略
        String baselineDesc = sqlOf(mysql, EQL_DESC, null);
        assertEquals(baselineDesc, sqlOf(mysql, EQL_DESC, false), "mysql desc nulls last == default ⇒ omit");
        // desc nulls first 与 DESC 缺省冲突 ⇒ 报错
        NopException e2 = featureError(mysql, EQL_DESC, true);
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e2.getErrorCode());
        assertEquals("nullsFirst(first)", e2.getParam("feature"));
    }

    @Test
    public void testMssqlConsistentRequestOmittedConflictThrows() {
        IDialect mssql = dialectOf("mssql");
        assertEquals(sqlOf(mssql, EQL_ASC, null), sqlOf(mssql, EQL_ASC, true), "mssql asc nulls first ⇒ omit");
        assertEquals("nullsFirst(last)", featureError(mssql, EQL_ASC, false).getParam("feature"));
        assertEquals(sqlOf(mssql, EQL_DESC, null), sqlOf(mssql, EQL_DESC, false), "mssql desc nulls last ⇒ omit");
        assertEquals("nullsFirst(first)", featureError(mssql, EQL_DESC, true).getParam("feature"));
    }

    // ---- 分支 2 集合外：dm/tdengine/db2 无一手文档 ⇒ 两方向一律报错（红线 [未取证]） ----

    @Test
    public void testUndocumentedDialectsAlwaysThrowOnExplicitRequest() {
        for (String name : new String[]{"dm", "tdengine", "db2"}) {
            IDialect dialect = dialectOf(name);
            assertEquals("nullsFirst(first)", featureError(dialect, EQL_ASC, true).getParam("feature"),
                    name + " asc nulls first must throw (undocumented default)");
            assertEquals("nullsFirst(last)", featureError(dialect, EQL_ASC, false).getParam("feature"),
                    name + " asc nulls last must throw (undocumented default)");
            assertEquals("nullsFirst(first)", featureError(dialect, EQL_DESC, true).getParam("feature"),
                    name + " desc nulls first must throw (undocumented default)");
            assertEquals("nullsFirst(last)", featureError(dialect, EQL_DESC, false).getParam("feature"),
                    name + " desc nulls last must throw (undocumented default)");
        }
    }

    // ---- flag 翻转因果（EC ③ 接线验证 #23：翻 flag 行为随之翻转，手构方言） ----

    @Test
    public void testFlippingDefaultNullsFirstFlipsGateOutcome() {
        // mysql 形（true）：asc first 一致省略
        IDialect firstDialect = handDialect("mysql", true, null);
        assertEquals(sqlOf(firstDialect, EQL_ASC, null), sqlOf(firstDialect, EQL_ASC, true));
        // 翻转为 false：asc first 变为冲突报错、asc last 变为一致省略——行为随 flag 翻转
        IDialect lastDialect = handDialect("mysql", false, null);
        assertEquals("nullsFirst(first)", featureError(lastDialect, EQL_ASC, true).getParam("feature"));
        assertEquals(sqlOf(lastDialect, EQL_ASC, null), sqlOf(lastDialect, EQL_ASC, false));
        // DESC 镜像随翻转：false ⇒ DESC 缺省 first（desc nulls first 省略、desc nulls last 报错）
        assertEquals(sqlOf(lastDialect, EQL_DESC, null), sqlOf(lastDialect, EQL_DESC, true));
        assertEquals("nullsFirst(last)", featureError(lastDialect, EQL_DESC, false).getParam("feature"));
    }

    @Test
    public void testFlippingSupportNullsFirstSwitchesToPrintBranch() {
        // 手构 mysql（defaultNullsFirst=true）+ supportNullsFirst=true：asc nulls last 从报错
        // 变为打印子句——support 分支优先于缺省序推导（正交两标志各司其职）
        IDialect dialect = handDialect("mysql", true, true);
        assertTrue(dialect.isSupportNullsFirst());
        assertTrue(sqlOf(dialect, EQL_ASC, false).contains("nulls last"));
        assertTrue(sqlOf(dialect, EQL_DESC, true).contains("nulls first"));
    }

    // ---- 集合外手构方言（name 不在已证集）⇒ 不可推导一律报错（未来新方言同纪律） ----

    @Test
    public void testUnknownDialectNameAlwaysThrows() {
        IDialect dialect = handDialect("minimal", true, null);
        assertEquals("nullsFirst(first)", featureError(dialect, EQL_ASC, true).getParam("feature"));
    }
}
