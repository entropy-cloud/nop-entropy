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
import io.nop.orm.model.OrmUniqueKeyModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_CONFLICT_COLUMNS;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_COLUMN_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2380 Phase 4：EQL {@code upsert} 的编译期行为——三方言 lowering + 四道编译期门。
 *
 * <p>权威设计：{@code ai-dev/design/eql-cross-db-compat/2026-10-10-eql-upsert-and-returning-design.md} §3.5 / §3.6。
 *
 * <p>本类存在的意义：把"跨库行为分叉"从运行期的静默错误提前成编译期的响亮失败。
 * 因此每条负向用例都断言到<b>错误码 + 参数</b>，不只是"抛了异常"。
 */
public class TestEqlUpsertCompileGate {

    static final String EQL_PK_CONFLICT =
            "upsert into AppUser (id, name, age) values (?, ?, ?) on conflict (id) "
                    + "do update set name = ?, age = ?";

    static final String EQL_UNIQUE_CONFLICT =
            "upsert into AppUser (id, name, age) values (?, ?, ?) on conflict (name) "
                    + "do update set age = ?";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ---- helpers ----

    static OrmEntityModel entityWithUniqueKey(List<String> uniqueKeyColumns) {
        OrmEntityModel m = TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "ID", 1, true),
                TestEqlCompileSql.col("name", "NAME", 2, false),
                TestEqlCompileSql.col("age", "AGE", 3, false));
        if (uniqueKeyColumns != null && !uniqueKeyColumns.isEmpty()) {
            OrmUniqueKeyModel key = new OrmUniqueKeyModel();
            key.setName("uk_name");
            key.setColumns(uniqueKeyColumns);
            m.addUniqueKey(key);
        }
        return m;
    }

    static TestEqlCompileSql.TestCompileContext ctxOf(String dialectName, List<String> uniqueKeyColumns) {
        TestEqlCompileSql.TestCompileContext ctx = new TestEqlCompileSql.TestCompileContext();
        IDialect dialect = io.nop.dao.dialect.DialectManager.instance().getDialect(dialectName);
        assertNotNull(dialect, () -> "dialect not loaded: " + dialectName);
        ctx.effectiveDialect = dialect;
        ctx.entities.put("AppUser", entityWithUniqueKey(uniqueKeyColumns));
        return ctx;
    }

    /**
     * 归一化打印器排版：折叠连续空白、去掉括号内侧空格、转小写。
     * 打印器的 {@code beginBraceBlock()} 会产生 {@code "( id, name )"} 这类带空白的形态，
     * 断言不应绑定排版细节。
     */
    static String norm(String sql) {
        return sql.replaceAll("\\s+", " ").replaceAll("\\(\\s+", "(").replaceAll("\\s+\\)", ")").toLowerCase();
    }

    static String sqlOf(String dialectName, List<String> uniqueKeyColumns, String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, ctxOf(dialectName, uniqueKeyColumns));
        return compiled.getSql().getText().replaceAll("\\s+", " ").trim();
    }

    static NopException gateError(String dialectName, List<String> uniqueKeyColumns, String eql,
                                  String expectedCode, String expectedFeature) {
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(dialectName, uniqueKeyColumns, eql),
                () -> dialectName + " must reject at compile time: " + eql);
        assertEquals(expectedCode, e.getErrorCode(), dialectName);
        if (expectedFeature != null) {
            assertEquals(expectedFeature, e.getParam("feature"), dialectName);
        }
        return e;
    }

    // ---- positive: 三方言 lowering ----

    @Test
    public void testPostgresqlLowering() {
        String sql = sqlOf("postgresql", List.of("name"), EQL_PK_CONFLICT);
        assertTrue(norm(sql).startsWith("insert into app_user"), sql);
        assertTrue(norm(sql).contains("on conflict (id)"), sql);
        assertTrue(norm(sql).contains("do update set"), sql);
    }

    @Test
    public void testMySqlLowering() {
        String sql = sqlOf("mysql", List.of("name"), EQL_PK_CONFLICT);
        assertTrue(norm(sql).startsWith("insert into app_user"), sql);
        assertTrue(norm(sql).contains("on duplicate key update"), sql);
        // MySQL 族完全忽略冲突键列表——不输出 on conflict
        assertTrue(!norm(sql).contains("on conflict"), "MySQL form must not emit on conflict: " + sql);
    }

    @Test
    public void testH2Lowering() {
        // H2 走 MERGE ... KEY ... VALUES；更新集必须等于列清单（含冲突键列本身），
        // 否则 transform 期的两条必要条件门会拒绝
        String sql = sqlOf("h2", List.of("name"),
                "upsert into AppUser (id, name, age) values (?, ?, ?) on conflict (id) "
                        + "do update set id = ?, name = ?, age = ?");
        assertTrue(norm(sql).startsWith("merge into app_user"), sql);
        assertTrue(norm(sql).contains("key (id)"), sql);
        assertTrue(norm(sql).contains("values("), sql);
        assertTrue(!norm(sql).contains("do update"), "H2 MERGE has no update clause: " + sql);
    }

    @Test
    public void testConflictKeyOnDeclaredUniqueKey() {
        String sql = sqlOf("postgresql", List.of("name"), EQL_UNIQUE_CONFLICT);
        assertTrue(norm(sql).contains("on conflict (name)"), sql);
    }

    // ---- gate 1: 方言能力 ----

    @Test
    public void testUnsupportedDialectFailsFast() {
        for (String dialect : List.of("oracle", "mssql", "dm", "tdengine")) {
            gateError(dialect, List.of("name"), EQL_PK_CONFLICT,
                    ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), "upsert");
        }
    }

    // ---- gate 2: 冲突键必须落在写入列清单内 ----

    @Test
    public void testConflictKeyNotInColumnListFails() {
        // h2 的 MERGE ... KEY(k) 要求键列在 VALUES 列清单内；PG/MySQL 不要求
        // —— 不统一就是跨库行为分叉，必须编译期统一为"必须 ⊆"
        NopException e = gateError("postgresql", List.of("name"),
                "upsert into AppUser (id, name) values (?, ?) on conflict (age) do update set name = ?",
                ERR_EQL_INVALID_CONFLICT_COLUMNS.getErrorCode(), null);
        assertEquals("age", e.getParam("conflictColumns"));
        assertEquals("AppUser", e.getParam("entityName"));
    }

    // ---- gate 3: 冲突键必须命中唯一键或主键 ----

    @Test
    public void testConflictKeyMatchingNeitherUniqueKeyNorPkFails() {
        // 实体只声明了 uk(name)，age 既不在唯一键也不是主键
        gateError("postgresql", List.of("name"),
                "upsert into AppUser (id, name, age) values (?, ?, ?) on conflict (age) "
                        + "do update set name = ?",
                ERR_EQL_INVALID_CONFLICT_COLUMNS.getErrorCode(), null);
    }

    @Test
    public void testConflictKeyWithoutAnyDeclaredUniqueKeyFails() {
        // 实体没有声明任何唯一键 ⇒ age 无处可匹配（name 也不在唯一键里）
        gateError("postgresql", null,
                "upsert into AppUser (id, name, age) values (?, ?, ?) on conflict (age) "
                        + "do update set name = ?",
                ERR_EQL_INVALID_CONFLICT_COLUMNS.getErrorCode(), null);
    }

    /**
     * 负向用例必须走<b>真实声明了唯一键</b>的模型：本例用 uk(name) 命中 name 作冲突键的正向，
     * 与"未声明任何唯一键"的负向配对，证明校验真的读了模型而不是恒放行。
     */
    @Test
    public void testUniqueKeyIsActuallyReadFromModel() {
        // 正向：声明了 uk(name) ⇒ name 作冲突键合法
        assertNotNull(sqlOf("postgresql", List.of("name"), EQL_UNIQUE_CONFLICT));

        // 负向：同一个模型（uk(name) 存在），age 仍不被接受 ⇒ 校验读到了 uk 且做了匹配
        gateError("postgresql", List.of("name"),
                "upsert into AppUser (id, name, age) values (?, ?, ?) on conflict (age) "
                        + "do update set name = ?",
                ERR_EQL_INVALID_CONFLICT_COLUMNS.getErrorCode(), null);
    }

    // ---- gate 4: H2 形态约束的两条必要条件 ----

    /**
     * 条件 1：更新集 ⊊ 列清单 —— H2 会把未列出的列一并覆盖为新值（静默语义分歧，禁）。
     */
    @Test
    public void testH2RejectsPartialUpdateSet() {
        gateError("h2", List.of("name"), EQL_PK_CONFLICT,
                ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), "upsertPartialUpdate");
    }

    /**
     * 条件 2：右值引用列 —— H2 的 MERGE ... KEY ... VALUES 没有更新子句，
     * VALUES 行内无法引用被更新行的旧值。<b>这条单靠条件 1 拦不住</b>
     * （更新集与列清单完全相同时仍会通过条件 1）。
     */
    @Test
    public void testH2RejectsColumnReferenceInUpdateRhs() {
        gateError("h2", List.of("name"),
                "upsert into AppUser (id, name, age) values (?, ?, ?) on conflict (id) "
                        + "do update set id = id, name = ?, age = ?",
                ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), "upsertPartialUpdate");
    }

    /**
     * 条件 1 的第二半：更新集 ⊋ 列清单（引用了未被插入的列）⇒ H2 无法表达该赋值。
     */
    @Test
    public void testH2RejectsUpdateSetIntroducingExtraColumn() {
        // 更新集 ⊋ 列清单：age 未被插入，H2 的 MERGE 无法表达该赋值
        NopException e = assertThrows(NopException.class, () -> sqlOf("h2", List.of("name"),
                "upsert into AppUser (id, name) values (?, ?) on conflict (id) "
                        + "do update set id = ?, name = ?, age = ?"),
                "update set must not introduce a column absent from the insert column list");
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("upsertPartialUpdate", e.getParam("feature"));
    }

    /** 非 H2 方言不受 H2 形态约束（同样的写法在 PG 上合法）。 */
    @Test
    public void testNonH2DialectAllowsColumnReferenceInUpdateRhs() {
        String sql = sqlOf("postgresql", List.of("name"),
                "upsert into AppUser (id, name, age) values (?, ?, ?) on conflict (id) "
                        + "do update set id = id, name = ?, age = ?");
        assertTrue(norm(sql).contains("do update set"), sql);
    }

    // ---- statement kind ----

    @Test
    public void testStatementKindAndWriteEntity() {
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL_PK_CONFLICT, ctxOf("postgresql", List.of("name")));
        assertEquals(io.nop.orm.eql.ast.SqlStatementKind.UPSERT, compiled.getStatementKind());
        assertEquals("AppUser", compiled.getWriteEntityName(),
                "write entity name drives the dao listener dispatch; null would silently skip reporting");
    }

    // ---- echo (toSqlString) 不 hollow ----

    @Test
    public void testEchoIsNotEmpty() {
        SqlUpsertEchoProbe.assertEchoNotEmpty(EQL_PK_CONFLICT);
    }

    static class SqlUpsertEchoProbe {
        static void assertEchoNotEmpty(String eql) {
            io.nop.orm.eql.ast.SqlProgram program = new io.nop.orm.eql.parse.EqlASTParser().parseFromText(null, eql);
            assertNotNull(program);
            String echo = program.toSqlString();
            assertNotNull(echo);
            assertTrue(echo.toLowerCase().contains("upsert"),
                    "echo must not be a hollow empty string: [" + echo + "]");
            assertTrue(echo.toLowerCase().contains("on conflict"), echo);
            assertTrue(echo.toLowerCase().contains("do update"), echo);
        }
    }

    @Test
    public void testUpdateSetTargetsMustBelongToTable() {
        // 更新集目标列必须能解析为实体上的列；解析期即失败
        gateError("postgresql", List.of("name"),
                "upsert into AppUser (id, name, age) values (?, ?, ?) on conflict (id) "
                        + "do update set notAColumn = ?",
                ERR_EQL_UNKNOWN_COLUMN_NAME.getErrorCode(), null);
    }

    @Test
    public void testConflictColumnNamesReportedInOrder() {
        NopException e = gateError("postgresql", List.of("name"),
                "upsert into AppUser (id, name, age) values (?, ?, ?) on conflict (id, age) "
                        + "do update set name = ?",
                ERR_EQL_INVALID_CONFLICT_COLUMNS.getErrorCode(), null);
        assertEquals("id,age", e.getParam("conflictColumns"),
                "conflictColumns param must carry the user-written prop names in order");
    }

    @Test
    public void testAllThreeNativeDialectsAgree() {
        // 一致性总钉：三种原生方言都必须接受同一条 upsert（冲突键 = 主键）
        for (String dialect : Arrays.asList("postgresql", "mysql", "mariadb", "h2")) {
            String eql = dialect.equals("h2")
                    ? "upsert into AppUser (id, name, age) values (?, ?, ?) on conflict (id) "
                            + "do update set id = ?, name = ?, age = ?"
                    : EQL_PK_CONFLICT;
            assertNotNull(sqlOf(dialect, List.of("name"), eql), dialect);
        }
    }

    @Test
    public void testPropNamesNotColumnCodesAreCompared() {
        // 冲突键校验比较的是 prop 名：实体里 name→NAME、age→AGE 是 prop/code 分离的，
        // 用 prop 名书写冲突键必须匹配得上（若错误地拿 code 比较，这里会失败）
        String sql = sqlOf("postgresql", List.of("name"), EQL_UNIQUE_CONFLICT);
        assertTrue(norm(sql).contains("on conflict (name)"),
                "prop-name conflict key must resolve to the physical column code in SQL: " + sql);
    }
}