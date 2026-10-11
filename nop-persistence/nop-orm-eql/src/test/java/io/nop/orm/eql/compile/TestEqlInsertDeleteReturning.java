/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/nop-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.model.OrmEntityModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2380 Phase 5：EQL {@code insert ... returning} / {@code delete ... returning}。
 *
 * <p>权威设计：{@code ai-dev/design/eql-cross-db-compat/2026-10-10-eql-upsert-and-returning-design.md} §3.2 / §3.3。
 *
 * <p>核心不变量有两条，缺一不可：
 * <ol>
 *   <li><b>正/负向分离</b>：两个新能力位的方言支持集合与既有的
 *       {@code supportReturningForUpdate} <b>不同</b>——duckdb 支持 insert/delete 的
 *       RETURNING 却不支持 update 的。任何把三者合并成一个标志的实现都会在这里露馅；</li>
 *   <li><b>别名抑制不得误伤</b>：returning 投影不带生成别名，但普通 SELECT 投影<b>必须</b>带。
 *       判定的常见错误写法 {@code instanceof SqlDmlStatement} 会把后者一并打掉，
 *       而既有断言多为子串匹配、不会转红——所以反向断言是必需的。</li>
 * </ol>
 */
public class TestEqlInsertDeleteReturning {

    static final String INSERT_RETURNING =
            "insert into AppUser (id, name) values (?, ?) returning id, name";
    static final String INSERT_RETURNING_ALL =
            "insert into AppUser (id, name) values (?, ?) returning *";
    static final String DELETE_RETURNING =
            "delete from AppUser o where o.id = ? returning o.id, o.name";

    /** 投影别名形态：{@code SeqAliasGenerator.genColumnAlias()} 产出 c1/c2/c3…（不是 col_N）。 */
    static final String GENERATED_ALIAS = " as c";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static TestEqlCompileSql.TestCompileContext ctxOf(String dialectName) {
        TestEqlCompileSql.TestCompileContext ctx = new TestEqlCompileSql.TestCompileContext();
        IDialect dialect = io.nop.dao.dialect.DialectManager.instance().getDialect(dialectName);
        assertNotNull(dialect, () -> "dialect not loaded: " + dialectName);
        ctx.effectiveDialect = dialect;
        OrmEntityModel m = TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "ID", 1, true),
                TestEqlCompileSql.col("name", "NAME", 2, false));
        ctx.entities.put("AppUser", m);
        return ctx;
    }

    static String norm(String sql) {
        return sql.replaceAll("\\s+", " ").replaceAll("\\(\\s+", "(").replaceAll("\\s+\\)", ")").toLowerCase();
    }

    static String sqlOf(String dialectName, String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, ctxOf(dialectName));
        return compiled.getSql().getText();
    }

    static NopException gateError(String dialectName, String eql, String expectedFeature) {
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(dialectName, eql),
                () -> dialectName + " must reject at compile time: " + eql);
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode(), dialectName);
        assertEquals(expectedFeature, e.getParam("feature"), dialectName);
        return e;
    }

    // ---- positive: 支持方言 ----

    @Test
    public void testPostgresqlInsertReturning() {
        String sql = norm(sqlOf("postgresql", INSERT_RETURNING));
        assertTrue(sql.contains("insert into app_user"), sql);
        assertTrue(sql.contains("returning id,name") || sql.contains("returning id, name"), sql);
    }

    @Test
    public void testPostgresqlDeleteReturning() {
        String sql = norm(sqlOf("postgresql", DELETE_RETURNING));
        assertTrue(sql.contains("delete"), sql);
        assertTrue(sql.contains("returning"), sql);
    }

    /** {@code returning *} 必须在 transform 期展开为实体全部列投影。 */
    @Test
    public void testReturningStarIsExpanded() {
        String sql = norm(sqlOf("postgresql", INSERT_RETURNING_ALL));
        assertTrue(sql.contains("returning"), sql);
        assertTrue(!sql.contains("returning *"), "returning * must be expanded, not printed raw: " + sql);
        assertTrue(sql.contains("id"), sql);
        assertTrue(sql.contains("name"), sql);
    }

    /** duckdb：insert/delete 的 RETURNING 支持但 update 的不支持——三个标志必须分立。 */
    @Test
    public void testDuckdbSupportsInsertDeleteReturningButNotUpdate() {
        String sql = norm(sqlOf("duckdb", INSERT_RETURNING));
        assertTrue(sql.contains("returning"), sql);

        assertTrue(io.nop.dao.dialect.DialectManager.instance().getDialect("duckdb").isSupportReturningForInsert());
        assertTrue(io.nop.dao.dialect.DialectManager.instance().getDialect("duckdb").isSupportReturningForDelete());
        assertTrue(!io.nop.dao.dialect.DialectManager.instance().getDialect("duckdb").isSupportReturningForUpdate(),
                "duckdb's UPDATE..RETURNING stays unsupported — the three flags must not be merged");
    }

    // ---- negative: 不支持方言（编译期响亮失败，断言到 feature 参数） ----

    @Test
    public void testInsertReturningUnsupportedDialects() {
        for (String dialect : List.of("mysql", "mariadb", "h2", "oracle", "mssql")) {
            gateError(dialect, INSERT_RETURNING, "supportReturningForInsert");
        }
    }

    @Test
    public void testDeleteReturningUnsupportedDialects() {
        for (String dialect : List.of("mysql", "mariadb", "h2", "oracle", "mssql")) {
            gateError(dialect, DELETE_RETURNING, "supportReturningForDelete");
        }
    }

    /** 既有 update 路径的方言取值不得被本次扩展改变（BC）。 */
    @Test
    public void testUpdateReturningGateUnchanged() {
        gateError("h2", "update AppUser o set o.name = 'x' where o.id = '1' returning o.name",
                "supportReturningForUpdate");
        gateError("mysql", "update AppUser o set o.name = 'x' where o.id = '1' returning o.name",
                "supportReturningForUpdate");
    }

    // ---- 别名抑制：正反两面 ----

    /**
     * 正向：returning 投影<b>不</b>带生成别名。用正则而非子串 —— 断言必须真的能抓住回归，
     * 写成"不含 col_N"这种错形态会让断言恒真（生成别名实际是 cN）。
     */
    @Test
    public void testReturningProjectionsHaveNoGeneratedAlias() {
        for (String eql : List.of(INSERT_RETURNING, INSERT_RETURNING_ALL, DELETE_RETURNING,
                "insert into AppUser (id, name) values (?, ?) returning id, upper(name)")) {
            String sql = norm(sqlOf("postgresql", eql));
            assertTrue(!sql.contains(GENERATED_ALIAS),
                    "returning projections must carry no generated alias: " + eql + " => " + sql);
        }
    }

    /**
     * 反向：普通 SELECT 投影<b>必须仍然带</b>生成别名。
     *
     * <p>这是 Plan 2380 审查中差点漏掉的必测项：returning 的别名抑制判定若误写成
     * {@code instanceof SqlDmlStatement}（{@code SqlSelect} 同继承该基类），会把所有 SELECT
     * 投影别名一并打掉，而既有断言多为子串匹配、不会转红。
     */
    @Test
    public void testPlainSelectKeepsGeneratedAlias() {
        String sql = norm(sqlOf("postgresql", "select id, name from AppUser"));
        assertTrue(sql.contains(GENERATED_ALIAS),
                "plain SELECT projections must keep generated aliases (c1/c2): " + sql);
    }

    @Test
    public void testSelectWithAggregateKeepsGeneratedAlias() {
        String sql = norm(sqlOf("postgresql", "select count(*) from AppUser"));
        assertTrue(sql.contains(GENERATED_ALIAS),
                "aggregate projection must keep its generated alias: " + sql);
    }

    // ---- 语句种类与写实体 ----

    @Test
    public void testStatementKindsAndWriteEntity() {
        ICompiledSql insert = new EqlCompiler().compile("test", INSERT_RETURNING, ctxOf("postgresql"));
        assertEquals(io.nop.orm.eql.ast.SqlStatementKind.INSERT, insert.getStatementKind());
        assertEquals("AppUser", insert.getWriteEntityName());

        ICompiledSql delete = new EqlCompiler().compile("test", DELETE_RETURNING, ctxOf("postgresql"));
        assertEquals(io.nop.orm.eql.ast.SqlStatementKind.DELETE, delete.getStatementKind());
        assertEquals("AppUser", delete.getWriteEntityName());
    }
}