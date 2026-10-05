/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code NULLS FIRST/LAST} 执行路径方言化发射的行为矩阵（真实方言绑定 + 手构方言
 * flag 因果）。
 *
 * <p>【merge note / Plan 2308 supersede】本类原为 ours WI7 Phase 2 三分门矩阵
 * （support 打印 / 缺省一致省略 / 冲突或集合外报错，裁定 wi7-phase0-adjudications §三）。
 * merge 裁决采纳 master Plan 2308 emission（support 位 gate + CASE 改写 + D2/D3/D4
 * fail-fast；翻案四点依据见 AstToSqlGenerator merge 裁决记录），矩阵按下表改写：
 * <ul>
 * <li>support=true（h2/postgresql/duckdb/oracle + db2 一手修正 0ea57a097e）：请求即原生
 * 打印；</li>
 * <li>{@code nullsFirst == null}：输出与未注入逐字节一致（BC 硬约束，分支 0 两侧一致）；</li>
 * <li>support 缺省 false（mysql/mssql/dm/tdengine/手构无名方言）：纯列引用显式请求走
 * NullsOrderRewriter CASE 改写（方向正确性四组合）；原「缺省一致省略 / 否则
 * ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE」口径退役；</li>
 * <li>结构不可改写（非列引用 / SELECT DISTINCT）⇒ nop.err.eql.nulls-order-not-supported
 * + reason（响亮失败原则保持）；</li>
 * <li>{@code defaultNullsFirst} 全程不读（Plan 2308 design §3.8：单布尔无法表达各方言
 * 真实空序模型）——flag 翻转不改变发射行为，本矩阵新增该不变式断言。</li>
 * </ul>
 * 与 master 侧 {@code TestEqlNullsOrderCompile}（手构方言 + EqlCompiler 全链路）互补：
 * 本类负责真实方言装载矩阵与程序化 AST 注入路径。
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
     * path 的 fileFullName）。merge 后发射判据只有 supportNullsFirst；defaultNullsFirst
     * 仅为配置事实登记（发射不读，见 {@link #testFlippingDefaultNullsFirstDoesNotChangeEmission}）。
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
     * generate），复刻 {@code EqlCompiler.compile} 核心。
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

    static NopException emitError(IDialect dialect, String eql, boolean nullsFirst) {
        return assertThrows(NopException.class, () -> sqlOf(dialect, eql, nullsFirst));
    }

    // ---- supportNullsFirst=true（真实方言 5 个，含 db2 一手修正）请求即原生打印 ----

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

    @Test
    public void testDb2PrintsRequestedNullsOrderAfterFirstHandFix() {
        // 0ea57a097e（master 一手修正，merge 联动）：db2 supportNullsFirst=true ⇒ 请求即原生
        // 直出（ours 原 [未取证] 报错口径退役——合并任务书明确指令项）
        assertTrue(sqlOf(dialectOf("db2"), EQL_ASC, true).contains("nulls first"));
        assertTrue(sqlOf(dialectOf("db2"), EQL_ASC, false).contains("nulls last"));
        assertTrue(sqlOf(dialectOf("db2"), EQL_DESC, true).contains("nulls first"));
        assertTrue(sqlOf(dialectOf("db2"), EQL_DESC, false).contains("nulls last"));
    }

    // ---- 分支 0：nullsFirst == null 逐字节不变（BC 硬约束，代表方言） ----

    @Test
    public void testNullUnsetOutputByteIdenticalOnMysqlAndOracle() {
        for (String name : new String[]{"mysql", "oracle", "tdengine", "db2"}) {
            IDialect dialect = dialectOf(name);
            String baselineAsc = sqlOf(dialect, EQL_ASC, null);
            assertFalse(baselineAsc.contains("nulls"), name + ": " + baselineAsc);
            // 分支 0 = 与未注入时的输出逐字节一致（同输入两次生成恒等）
            assertEquals(baselineAsc, sqlOf(dialect, EQL_ASC, null), name);
        }
    }

    // ---- support 缺省 false 方言：纯列引用显式请求走 CASE 改写（方向正确性四组合） ----

    @Test
    public void testMysqlCaseRewriteDirectionMatrix() {
        IDialect mysql = dialectOf("mysql");
        String ascFirst = sqlOf(mysql, EQL_ASC, true);
        assertTrue(ascFirst.matches("(?s).*SID is null then 1 else 0 end desc.*"), ascFirst);
        assertTrue(ascFirst.contains("SID asc"), ascFirst);
        assertFalse(ascFirst.contains("nulls"), ascFirst);

        String ascLast = sqlOf(mysql, EQL_ASC, false);
        assertTrue(ascLast.matches("(?s).*SID is null then 0 else 1 end desc.*"), ascLast);

        String descFirst = sqlOf(mysql, EQL_DESC, true);
        assertTrue(descFirst.matches("(?s).*SID is null then 0 else 1 end asc.*"), descFirst);
        assertTrue(descFirst.contains("SID desc"), descFirst);

        String descLast = sqlOf(mysql, EQL_DESC, false);
        assertTrue(descLast.matches("(?s).*SID is null then 1 else 0 end asc.*"), descLast);
    }

    @Test
    public void testUndocumentedDialectsCaseRewriteOnExplicitRequest() {
        // 原「集合外一律报错」口径退役：mssql/dm/tdengine 纯列引用显式请求 CASE 无损模拟
        for (String name : new String[]{"mssql", "dm", "tdengine"}) {
            IDialect dialect = dialectOf(name);
            String ascFirst = sqlOf(dialect, EQL_ASC, true);
            assertTrue(ascFirst.matches("(?s).*SID is null then 1 else 0 end desc.*"),
                    name + " asc nulls first must case-rewrite: " + ascFirst);
            String descLast = sqlOf(dialect, EQL_DESC, false);
            assertTrue(descLast.matches("(?s).*SID is null then 1 else 0 end asc.*"),
                    name + " desc nulls last must case-rewrite: " + descLast);
        }
    }

    // ---- 结构不可改写处 fail-fast（真实方言端到端：非列引用 / DISTINCT） ----

    @Test
    public void testNonColumnExpressionFailsFast() {
        NopException e = emitError(dialectOf("mysql"),
                "select o.name from AppDept o order by upper(o.name) nulls last", false);
        assertEquals("nop.err.eql.nulls-order-not-supported", e.getErrorCode());
        assertEquals("expression-not-duplicable", e.getParam("reason"));
    }

    @Test
    public void testDistinctSelectFailsFast() {
        NopException e = emitError(dialectOf("mysql"),
                "select distinct o.name from AppDept o order by o.id nulls last", false);
        assertEquals("nop.err.eql.nulls-order-not-supported", e.getErrorCode());
        assertEquals("distinct-not-supported", e.getParam("reason"));
    }

    @Test
    public void testFunctionExpressionAllowedOnNativeDialect() {
        // support=true 方言不需要复制表达式，函数排序项 + 显式空序合法（D2 只约束改写路径）
        assertTrue(sqlOf(dialectOf("postgresql"),
                "select o.name from AppDept o order by upper(o.name) nulls last", false)
                .contains("nulls last"));
    }

    // ---- flag 因果（手构方言）：support 翻转切换发射分支；defaultNullsFirst 不再被读 ----

    @Test
    public void testFlippingSupportNullsFirstSwitchesEmission() {
        // 手构 mysql 形方言：support 缺省（false）⇒ CASE 改写；翻 true ⇒ 原生打印
        IDialect rewriteDialect = handDialect("mysql", true, null);
        assertTrue(sqlOf(rewriteDialect, EQL_ASC, false)
                .matches("(?s).*SID is null then 0 else 1 end desc.*"));
        IDialect nativeDialect = handDialect("mysql", true, true);
        assertTrue(nativeDialect.isSupportNullsFirst());
        assertTrue(sqlOf(nativeDialect, EQL_ASC, false).contains("nulls last"));
    }

    @Test
    public void testFlippingDefaultNullsFirstDoesNotChangeEmission() {
        // Plan 2308 design §3.8：发射全程不读 defaultNullsFirst（单布尔无法表达各方言
        // 真实空序模型）——翻 flag 发射产物不变（ours 原"缺省一致省略随 flag 翻转"口径退役）
        IDialect firstDialect = handDialect("mysql", true, null);
        IDialect lastDialect = handDialect("mysql", false, null);
        assertEquals(sqlOf(firstDialect, EQL_ASC, true), sqlOf(lastDialect, EQL_ASC, true));
        assertEquals(sqlOf(firstDialect, EQL_ASC, false), sqlOf(lastDialect, EQL_ASC, false));
    }

    // ---- 集合外手构方言（name 不在任何已证集）⇒ 同一 support 判据，无名字特殊门 ----

    @Test
    public void testUnknownDialectNameCaseRewritesOnColumnRef() {
        // 原「未知方言名一律报错」口径退役：发射判据只有 support 位，与方言名无关
        IDialect dialect = handDialect("minimal", true, null);
        assertTrue(sqlOf(dialect, EQL_ASC, true)
                .matches("(?s).*SID is null then 1 else 0 end desc.*"));
    }
}
