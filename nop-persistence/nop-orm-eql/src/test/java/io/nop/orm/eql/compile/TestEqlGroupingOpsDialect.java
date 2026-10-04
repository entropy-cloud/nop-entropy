/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlGroupingOp;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.compile.TestEqlCompileSql.TestCompileContext;
import io.nop.orm.eql.enums.SqlGroupingOpType;
import io.nop.orm.eql.parse.EqlASTParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_FUNC_TOO_MANY_ARGS;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7b Phase 2（roadmap EC ①⑦；ⓒ-A 裁定
 * `ai-dev/design/eql-cross-db-compat/2026-10-04-wi7b-phase0-adjudications.md` §3-§5）：
 * `rollup`/`cube`（新 AST 节点 `SqlGroupingOp` + sqlGroupByItem 前置备选 + supportRollup/
 * supportCube 方言门）与 `grouping`（零文法——函数表注册 + 既有 min/max 元数通路）。
 *
 * <p>修复前红（`_tmp/wi7b/red-phase2.log`）：三函数在全部方言上都是
 * `nop.err.eql.unknown-function` 响亮编译期报错（规划中"注册"的起点即此）。
 */
public class TestEqlGroupingOpsDialect {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static TestCompileContext newContext(String dialectName) {
        IDialect dialect = DialectManager.instance().getDialect(dialectName);
        assertNotNull(dialect, "dialect not loaded: " + dialectName);
        TestCompileContext ctx = new TestCompileContext();
        ctx.entities.put("AppUser", TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true),
                TestEqlCompileSql.col("name", "NAME", 2, false)));
        ctx.effectiveDialect = dialect;
        return ctx;
    }

    static String compile(String dialectName, String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, newContext(dialectName));
        return compiled.getSql().getText();
    }

    static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ");
    }

    static SqlGroupingOp findGroupingOp(EqlASTNode node) {
        if (node instanceof SqlGroupingOp)
            return (SqlGroupingOp) node;
        SqlGroupingOp[] found = new SqlGroupingOp[1];
        node.forEachChild(child -> {
            if (found[0] == null)
                found[0] = findGroupingOp(child);
        });
        return found[0];
    }

    // ===== 文法与 AST 构建（ⓒ-A wrapper 模式） =====

    @Test
    public void testRollupParsesToGroupingOpNode() {
        SqlProgram program = new EqlASTParser().parseFromText(null,
                "select sum(a) from T t group by rollup(a, b)");
        SqlGroupingOp op = findGroupingOp(program);
        assertNotNull(op, "rollup must build SqlGroupingOp (备选前置胜者守护)");
        assertEquals(SqlGroupingOpType.ROLLUP, op.getType());
        assertEquals(2, op.getArgs().size());
    }

    @Test
    public void testCubeParsesToGroupingOpNode() {
        SqlProgram program = new EqlASTParser().parseFromText(null,
                "select sum(a) from T t group by cube(a)");
        SqlGroupingOp op = findGroupingOp(program);
        assertNotNull(op);
        assertEquals(SqlGroupingOpType.CUBE, op.getType());
        assertEquals(1, op.getArgs().size());
    }

    @Test
    public void testMixedGroupByItemsBuild() {
        // 混合项 GROUP BY a, ROLLUP(b,c)：同一 items 列表内逐项 accept() 分发
        SqlProgram program = new EqlASTParser().parseFromText(null,
                "select sum(a) from T t group by a, rollup(b, c)");
        SqlGroupingOp op = findGroupingOp(program);
        assertNotNull(op);
        assertEquals(SqlGroupingOpType.ROLLUP, op.getType());
    }

    /** 既有 group by 形态逐字不变守护（回显路径）。 */
    @Test
    public void testPlainGroupByEchoUnchanged() {
        SqlProgram program = new EqlASTParser().parseFromText(null,
                "select sum(a) from T t group by a, b");
        String echo = normalized(program.toSqlString());
        assertTrue(echo.contains("group by a, b"), echo);
        org.junit.jupiter.api.Assertions.assertNull(findGroupingOp(program),
                "plain group by must not contain SqlGroupingOp");
    }

    // ===== 正向：注册方言输出规范文本（EC ①，identity lowering） =====

    @ParameterizedTest
    @ValueSource(strings = {"postgresql", "duckdb", "oracle", "mssql"})
    void testRollupTextOnSupportingDialects(String dialectName) {
        // 列名按方言列映射分叉（SID/NAME/引号大小写），关键字形态严格断言大写 ROLLUP(
        String sql = normalized(compile(dialectName,
                "select sum(o.id) from AppUser o group by rollup(o.name)"));
        assertTrue(sql.toUpperCase().contains("GROUP BY ROLLUP(O."), sql);
    }

    @ParameterizedTest
    @ValueSource(strings = {"postgresql", "duckdb", "oracle", "mssql"})
    void testCubeTextOnSupportingDialects(String dialectName) {
        String sql = normalized(compile(dialectName,
                "select sum(o.id) from AppUser o group by cube(o.name, o.id)"));
        assertTrue(sql.toUpperCase().matches("(?s).*GROUP BY CUBE\\([^)]*,[^)]*\\).*"), sql);
    }

    @Test
    public void testMixedItemsTextOnPostgresql() {
        String sql = normalized(compile("postgresql",
                "select sum(o.id) from AppUser o group by o.name, rollup(o.id)"));
        assertTrue(sql.toUpperCase().matches("(?s).*GROUP BY O\\..*ROLLUP\\(O\\..*"), sql);
    }

    // ===== 守护用例：备选顺序与双路径胜者（裁定 §3.1） =====

    /**
     * 双路径胜者守护：h2 上 `group by rollup(g)` 必须命中新 AST 路径的能力门
     * （NOT_SUPPORT_FEATURE），**不得**命中 unknown-function（那是旧 SqlRegularFunction
     * 路径胜出 = 新备选沦为死代码的症候）。
     */
    @Test
    public void testRollupGateNotUnknownFunctionOnH2() {
        NopException e = assertThrows(NopException.class, () -> compile("h2",
                "select sum(o.id) from AppUser o group by rollup(o.name)"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode(),
                "备选顺序胜者守护：必须走 SqlGroupingOp 能力门而非函数表兜底");
        assertEquals("rollup", e.getParam("feature"));
    }

    // ===== 负向：显式编译期报错（EC ① 硬性 + 红线方言） =====

    /** EC ① 硬性：CUBE 在 MySQL/MariaDB 显式编译期报错（两方言各一）。 */
    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mariadb"})
    void testCubeExplicitErrorOnMysqlFamily(String dialectName) {
        NopException e = assertThrows(NopException.class, () -> compile(dialectName,
                "select sum(o.id) from AppUser o group by cube(o.name)"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("cube", e.getParam("feature"));
    }

    /** mysql 函数式 ROLLUP 8.4.3+ 超承诺基线 8.0.31 → 报错；mariadb 无函数式 ROLLUP → 报错；dm 红线继承 default=false → 报错。 */
    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mariadb", "mysql5.7", "dm", "tdengine"})
    void testRollupExplicitErrorOnExcludedDialects(String dialectName) {
        NopException e = assertThrows(NopException.class, () -> compile(dialectName,
                "select sum(o.id) from AppUser o group by rollup(o.name)"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode(), dialectName);
        assertEquals("rollup", e.getParam("feature"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dm", "tdengine", "h2"})
    void testCubeExplicitErrorOnExcludedDialects(String dialectName) {
        NopException e = assertThrows(NopException.class, () -> compile(dialectName,
                "select sum(o.id) from AppUser o group by cube(o.name)"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode(), dialectName);
        assertEquals("cube", e.getParam("feature"));
    }

    /**
     * 非 GROUP BY 位置：rollup(x)/cube(x) 仍解析为 SqlRegularFunction，函数表永不注册 →
     * unknown-function（裁定 §3.3——ⓒ-A 不获得文法级放置排他，放置排他来自"GROUP BY 之外无注册"）。
     */
    @ParameterizedTest
    @ValueSource(strings = {"postgresql", "h2"})
    void testNonGroupByPositionUnknownFunction(String dialectName) {
        NopException e1 = assertThrows(NopException.class, () -> compile(dialectName,
                "select rollup(o.id) from AppUser o"));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e1.getErrorCode(), dialectName);
        assertEquals("rollup", e1.getParam("funcName"));

        NopException e2 = assertThrows(NopException.class, () -> compile(dialectName,
                "select cube(o.id) from AppUser o"));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e2.getErrorCode(), dialectName);
        assertEquals("cube", e2.getParam("funcName"));
    }

    // ===== grouping（零文法——函数表注册 + 既有元数通路） =====

    @Test
    public void testGroupingTextOnPostgresql() {
        String sql = normalized(compile("postgresql",
                "select o.name, grouping(o.name) from AppUser o group by o.name having grouping(o.name) = 0"));
        assertTrue(sql.toUpperCase().contains("GROUPING(O."), sql);
        assertTrue(sql.toUpperCase().contains("HAVING GROUPING(O."), sql);
    }

    /** 继承面：duckdb 继承 postgresql、oracle-reverse 继承 oracle 的 grouping 注册。 */
    @ParameterizedTest
    @ValueSource(strings = {"duckdb", "oracle-reverse"})
    void testGroupingInheritedRegistrations(String dialectName) {
        String sql = normalized(compile(dialectName,
                "select o.name, grouping(o.name) from AppUser o group by o.name"));
        assertTrue(sql.toUpperCase().contains("GROUPING(O."), sql);
    }

    /** PG 位掩码多参（1..64）。 */
    @Test
    public void testGroupingMultiArgOnPostgresql() {
        String sql = normalized(compile("postgresql",
                "select grouping(o.name, o.id) from AppUser o group by o.name, o.id"));
        assertTrue(sql.toUpperCase().matches("(?s).*GROUPING\\([^)]*,[^)]*\\).*"), sql);
    }

    /** 元数按方言：oracle/mssql 单参（[文档]），2 参编译期 TOO_MANY_ARGS（既有通路，零新门）。 */
    @ParameterizedTest
    @ValueSource(strings = {"oracle", "mssql"})
    void testGroupingSingleArgOnOracleAndMssql(String dialectName) {
        NopException e = assertThrows(NopException.class, () -> compile(dialectName,
                "select o.name, grouping(o.name, o.id) from AppUser o group by o.name, o.id"));
        assertEquals(ERR_EQL_FUNC_TOO_MANY_ARGS.getErrorCode(), e.getErrorCode(), dialectName);
    }

    /** h2 探针 [实测] 无 GROUPING（90022）→ 不注册 → unknown-function（负向载体）。 */
    @Test
    public void testGroupingUnknownOnH2() {
        NopException e = assertThrows(NopException.class, () -> compile("h2",
                "select o.name, grouping(o.name) from AppUser o group by o.name"));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
        assertEquals("grouping", e.getParam("funcName"));
    }

    /**
     * mysql/mariadb 不注册（裁定 §4：EQL 无法为其生成 super-aggregate 行 ⇒ 死能力不注册）；
     * dm/tdengine 红线 [未取证] 不注册——全部 unknown-function 响亮。
     */
    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mariadb", "mysql5.7", "dm", "tdengine"})
    void testGroupingUnknownOnUnregisteredDialects(String dialectName) {
        NopException e = assertThrows(NopException.class, () -> compile(dialectName,
                "select o.name, grouping(o.name) from AppUser o group by o.name"));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode(), dialectName);
        assertEquals("grouping", e.getParam("funcName"));
    }

    // ===== 约束 4：一致性/不等价对照 =====

    /**
     * 同一 rollup 查询：支持方言（postgresql）输出原文规范形态（一致性）vs 不支持方言
     * （h2）编译期响亮报错（不等价 = 能力边界）。
     */
    @Test
    public void testRollupConsistencyVsInequivalence() {
        String pg = normalized(compile("postgresql",
                "select sum(o.id) from AppUser o group by rollup(o.name)"));
        assertTrue(pg.toUpperCase().contains("GROUP BY ROLLUP(O."), pg);

        NopException e = assertThrows(NopException.class, () -> compile("h2",
                "select sum(o.id) from AppUser o group by rollup(o.name)"));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
    }

    // ===== 端到端接线（guide 规则 #23）：h2 真库上的 super-aggregate 行 =====

    /**
     * 端到端：`group by rollup` 生成 SQL 打回内存 H2？——h2 无 ROLLUP（探针 90022），真库执行
     * 载体不可得（裁定 §5：h2 = 负向载体）；接线证明由文本断言 + 胜者守护承担，真库 super-aggregate
     * 语义归 CI 外方言（oracle/pg/duckdb/mssql 文档引证 + 文本断言替代证据，EC ⑦ 分派）。
     */
    @Test
    public void testRollupWiringEndToEndText() {
        // parse（前置备选）→ build（wrapper）→ transform（能力门）→ 打印（identity lowering）
        for (String d : new String[]{"postgresql", "duckdb", "oracle", "mssql"})
            assertTrue(normalized(compile(d,
                    "select sum(o.id) from AppUser o group by rollup(o.name)")).toUpperCase()
                    .contains("GROUP BY ROLLUP(O."), d);
    }
}
