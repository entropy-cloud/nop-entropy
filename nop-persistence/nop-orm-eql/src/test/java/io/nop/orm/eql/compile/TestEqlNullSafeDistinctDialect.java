/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.parse.EqlASTParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8 Phase 1（plan 27；审查 Major-2 必选件）：SAFE_EQ 渲染三分支——
 * mysql 族 = {@code <=>}；native 集合 = {@code IS [NOT] DISTINCT FROM} 方向文本直出
 * （禁止 {@code not(a is not distinct from b)} 包装）；其余方言 = 响亮
 * {@code nop.err.eql.dialect-not-support-feature}（无 transformer 时 oracle 亦落此分支）。
 *
 * <p>裁定：{@code ai-dev/design/eql-cross-db-compat/2026-10-04-wi8-phase0-adjudications.md}
 * §四.1。修复前红：{@code IS [NOT] DISTINCT FROM} 解析层失败，无任何输出路径。</p>
 *
 * <p>接线验证（Minimum Rules #23）：{@link #testH2NativeTextFullChain} 一条用例覆盖
 * parse → 构建期归一化 → EqlCompiler 无 transformer 方言渲染全链。</p>
 */
public class TestEqlNullSafeDistinctDialect {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static TestEqlCompileSql.TestCompileContext newContext(IDialect dialect) {
        assertNotNull(dialect);
        TestEqlCompileSql.TestCompileContext ctx = new TestEqlCompileSql.TestCompileContext();
        ctx.entities.put("AppUser", TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true),
                TestEqlCompileSql.col("name", "NAME", 2, false)));
        ctx.effectiveDialect = dialect;
        return ctx;
    }

    static String compile(IDialect dialect, String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, newContext(dialect));
        return compiled.getSql().getText().replaceAll("\\s+", " ");
    }

    // ===== 分支 2：native 集合（方向文本直出） =====

    @ParameterizedTest
    @ValueSource(strings = {"h2", "postgresql", "duckdb", "mssql"})
    public void testNativeIsNotDistinctFromDirectText(String dialectName) {
        String sql = compile(DialectManager.instance().getDialect(dialectName),
                "select u.id from AppUser u where u.id is not distinct from u.name");
        assertTrue(sql.contains(" is not distinct from "), sql);
        assertTrue(!sql.contains("<=>"), "native 分支不得输出 <=>: " + sql);
    }

    @ParameterizedTest
    @ValueSource(strings = {"h2", "postgresql", "duckdb", "mssql"})
    public void testNativeIsDistinctFromDirectTextNoNotWrapper(String dialectName) {
        String sql = compile(DialectManager.instance().getDialect(dialectName),
                "select u.id from AppUser u where u.id is distinct from u.name");
        assertTrue(sql.contains(" is distinct from "), sql);
        assertTrue(!sql.contains("not ("), "IS DISTINCT FROM 方向文本直出，禁止 not(...) 包装: " + sql);
        assertTrue(!sql.contains("<=>"), sql);
    }

    // ===== 分支 1：mysql 族（<=> 承载） =====

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mysql5.7", "mariadb"})
    public void testMysqlFamilySafeEqText(String dialectName) {
        String sql = compile(DialectManager.instance().getDialect(dialectName),
                "select u.id from AppUser u where u.id is not distinct from u.name");
        assertTrue(sql.contains("<=>"), sql);
        assertTrue(!sql.contains("distinct"), "mysql 分支输出 <=> 而非 IS DISTINCT FROM 文本: " + sql);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mysql5.7", "mariadb"})
    public void testMysqlFamilyIsDistinctFromNotWrappedSafeEq(String dialectName) {
        String sql = compile(DialectManager.instance().getDialect(dialectName),
                "select u.id from AppUser u where u.id is distinct from u.name");
        // MySQL 3VL：<=> 恒二值，not 良定义（裁定文档 §四.1 / 约束4 差异①）
        assertTrue(sql.contains("not (" + " ") || sql.contains("not ("), sql);
        assertTrue(sql.contains("<=>"), sql);
        assertTrue(!sql.contains("distinct"), sql);
    }

    // ===== 分支 3：其余方言响亮报错（fail-closed，无静默裸语法） =====

    @Test
    public void testUnsupportedConstructedDialectFailsLoud() {
        // 手构方言先例：TestEqlLateralSubqueryTableSource.buildDialect；名称不在任何集合 ⇒ 分支 3
        IDialect dialect = buildPlainDialect();
        NopException ex = assertThrows(NopException.class, () -> compile(dialect,
                "select u.id from AppUser u where u.id is not distinct from u.name"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), ex.getErrorCode());
        assertEquals("is[not] distinct from", ex.getParams().get("feature"));
    }

    @Test
    public void testOracleWithoutTransformerFailsLoud() {
        // 无 transformer 配置时 oracle 落渲染第 3 支 = 响亮，不静默输出 IS DISTINCT FROM 文本
        // （Phase 3 默认注入 transformer 后 oracle 走三节点展开，不再到达本分支）
        IDialect dialect = DialectManager.instance().getDialect("oracle");
        assertNotNull(dialect);
        NopException ex = assertThrows(NopException.class, () -> compile(dialect,
                "select u.id from AppUser u where u.id is not distinct from u.name"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), ex.getErrorCode());
    }

    // ===== 接线验证（#23）：parse → 归一化 → 无 transformer 方言渲染全链 =====

    @Test
    public void testH2NativeTextFullChain() {
        String sql = compile(DialectManager.instance().getDialect("h2"),
                "select u.id from AppUser u where u.id is not distinct from u.name");
        // 列名映射为物理列（SID/NAME），别名保持；h2 走 native 直出分支
        assertTrue(sql.matches("(?s).*from APP_USER as u.*"), "表名+别名保持: " + sql);
        assertTrue(sql.contains("u.SID is not distinct from u.NAME"), sql);
    }

    // ===== 回显守护：规范 AST 回显与方言无关（三态断言的「规范回显」面） =====

    @Test
    public void testCanonicalEchoIndependentOfDialect() {
        SqlProgram program = new EqlASTParser().parseFromText(null,
                "select u.id from AppUser u where u.id is distinct from u.name");
        String echo = program.toSqlString().replaceAll("\\s+", " ").trim();
        assertEquals("select u.id from AppUser as u where not ( u.id <=> u.name )", echo);
    }

    // ===== SqlOperator.SAFE_EQ 枚举边界（裁定文档 §六.1） =====

    @Test
    public void testSafeEqOperatorBoundaries() {
        assertEquals("<=>", SqlOperator.SAFE_EQ.getText());
        assertEquals(110, SqlOperator.SAFE_EQ.getPriority());
        assertTrue(SqlOperator.SAFE_EQ.isCompareOp(), "SAFE_EQ 纳入 isCompareOp（参数类型回退解析与 EQ 同路）");
        assertEquals(SqlOperator.SAFE_EQ, SqlOperator.SAFE_EQ.reverse(), "自反，不得落 null");
    }

    static IDialect buildPlainDialect() {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(java.util.Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        model.setSqls(sqls);
        model.setSqlDataTypes(java.util.Collections.emptyList());
        // DialectImpl 构造期无条件 buildFunctionSql("current_timestamp")，须注册（先例
        // TestEqlLateralSubqueryTableSource.newDialectModel 同款）
        io.nop.dao.dialect.model.SqlNativeFunctionModel currentTimestamp = new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        model.setFunctions(java.util.Collections.singletonList(currentTimestamp));
        io.nop.dao.dialect.model.DialectFeatures features = new io.nop.dao.dialect.model.DialectFeatures();
        model.setFeatures(features);
        return new DialectImpl(model);
    }
}
