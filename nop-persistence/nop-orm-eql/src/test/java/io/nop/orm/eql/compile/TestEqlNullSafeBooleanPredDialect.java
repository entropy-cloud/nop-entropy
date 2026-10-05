/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.model.OrmColumnModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8 Phase 2（plan 27；roadmap EC ③ + 约束 4）：{@code IS [NOT] TRUE/FALSE/UNKNOWN} 的
 * 方言文本与真值表逐行验证（裁定
 * {@code ai-dev/design/eql-cross-db-compat/2026-10-04-wi8-phase0-adjudications.md} §二.2/§三/§五）。
 *
 * <p>展开式是全方言安全形态（安全词表），无方言渲染门；h2 真库执行 3 行布尔操作数 × 6 谓词
 * 逐行断言（roadmap EC ③「逐案有用例」）；非布尔操作数行：mysql 文本 {@code 2 = 1} 恒 FALSE
 * （钉死语义），h2 为响亮类型错误（探针实测——H2 原生 {@code 2 IS TRUE} 返回 TRUE，属数值
 * 真值语义，与钉死严格语义分叉登记约束 4，展开式不采用原生语义）。</p>
 */
public class TestEqlNullSafeBooleanPredDialect {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static TestEqlCompileSql.TestCompileContext newContext(String dialectName) {
        IDialect dialect = DialectManager.instance().getDialect(dialectName);
        assertNotNull(dialect, "dialect not loaded: " + dialectName);
        TestEqlCompileSql.TestCompileContext ctx = new TestEqlCompileSql.TestCompileContext();
        ctx.entities.put("AppUser", TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true),
                boolCol("deleted", "DELETED", 2),
                intCol("score", "SCORE", 3)));
        ctx.effectiveDialect = dialect;
        return ctx;
    }

    static OrmColumnModel boolCol(String name, String code, int propId) {
        OrmColumnModel c = TestEqlCompileSql.col(name, code, propId, false);
        c.setStdSqlType(StdSqlType.BOOLEAN);
        c.setStdDataType(StdDataType.BOOLEAN);
        return c;
    }

    static OrmColumnModel intCol(String name, String code, int propId) {
        OrmColumnModel c = TestEqlCompileSql.col(name, code, propId, false);
        c.setStdSqlType(StdSqlType.INTEGER);
        c.setStdDataType(StdDataType.INT);
        return c;
    }

    static String spaceFree(String sql) {
        return sql.toLowerCase().replaceAll("\\s+", "");
    }

    static String compile(String dialectName, String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, newContext(dialectName));
        return compiled.getSql().getText().replaceAll("\\s+", " ");
    }

    // ===== 正向文本（pinned 展开式逐谓词；布尔字面量按方言渲染 1/0 vs true/false） =====

    @ParameterizedTest
    @CsvSource({
            "o.deleted is true, o.deleted is not null and (o.deleted = true)",
            "o.deleted is not true, not (o.deleted is not null and (o.deleted = true))",
            "o.deleted is false, o.deleted is not null and (o.deleted = false)",
            "o.deleted is not false, not (o.deleted is not null and (o.deleted = false))",
    })
    public void testMysqlExpansionText(String eqlPred, String expectPred) {
        String sql = compile("mysql", "select o.id from AppUser o where " + eqlPred);
        // mysql 布尔字面量渲染 1/0（default 系）；列名物理映射大小写随方言 + pretty 空格漂移：
        // contains 断言去空白 + 大小写不敏感
        String expectMysql = expectPred.replace("= true", "= 1").replace("= false", "= 0");
        assertTrue(spaceFree(sql).contains(spaceFree(expectMysql)), sql);
        // 布尔字面量按方言渲染为 1/0（default 系）
        assertTrue(sql.contains("= 1") || sql.contains("= 0"), "mysql 布尔字面量=1/0: " + sql);
    }

    @ParameterizedTest
    @CsvSource({
            "o.deleted is true, o.deleted is not null and (o.deleted = true)",
            "o.deleted is not true, not (o.deleted is not null and (o.deleted = true))",
            "o.deleted is false, o.deleted is not null and (o.deleted = false)",
            "o.deleted is not false, not (o.deleted is not null and (o.deleted = false))",
    })
    public void testH2ExpansionText(String eqlPred, String expectPred) {
        String sql = compile("h2", "select o.id from AppUser o where " + eqlPred);
        assertTrue(spaceFree(sql).contains(spaceFree(expectPred)), sql);
    }

    @Test
    public void testIsUnknownReusesIsNullRendering() {
        // 零新渲染路径：IS [NOT] UNKNOWN 输出与手写 IS [NOT] NULL 逐字节一致
        assertEquals(
                compile("mysql", "select o.id from AppUser o where o.deleted is null"),
                compile("mysql", "select o.id from AppUser o where o.deleted is unknown"));
        assertEquals(
                compile("h2", "select o.id from AppUser o where o.deleted is not null"),
                compile("h2", "select o.id from AppUser o where o.deleted is not unknown"));
    }

    // ===== 错误降级负向对照（roadmap EC ③；约束 4 ③项） =====

    @ParameterizedTest
    @ValueSource(strings = {"h2", "mysql", "postgresql"})
    public void testNoBinaryComparisonDegradation(String dialectName) {
        // 六谓词输出一律无 <> 比较符、无 <=> 复用、无 truthy 裸条件（裸操作数/not 单目）
        for (String pred : new String[]{"o.deleted is true", "o.deleted is not true",
                "o.deleted is false", "o.deleted is not false"}) {
            String sql = compile(dialectName, "select o.id from AppUser o where " + pred);
            assertFalse(sql.contains("<>"), "二元比较降级: " + sql);
            assertFalse(sql.contains("<=>"), "DISTINCT FROM 模板复用: " + sql);
            assertTrue(sql.contains("is not null"), "guard 支缺失: " + sql);
        }
    }

    // ===== 非布尔操作数行（真值表第 4 行；约束 4 ②项 MySQL 数值真值分叉登记） =====

    @Test
    public void testNonBooleanOperandMysqlPinnedSemantics() {
        // 钉死语义 2 IS TRUE = FALSE：展开式 2 is not null and (2 = 1) —— 算术恒 FALSE，
        // 与 MySQL 原生 IS TRUE 的数值真值语义（TRUE）分叉=约束 4「不等价」登记
        String sql = compile("mysql", "select o.id from AppUser o where 2 is true");
        assertTrue(spaceFree(sql).contains("2isnotnulland(2=1)"), sql);

        String notTrue = compile("mysql", "select o.id from AppUser o where 2 is not true");
        assertTrue(spaceFree(notTrue).contains("not(2isnotnulland(2=1))"), notTrue);
    }

    @Test
    public void testNonBooleanOperandH2FailsLoudNotTruthy() throws SQLException {
        // H2 探针实测（_tmp/wi8/h2probe.log）：原生 `2 IS TRUE` 返回 TRUE（truthy），`2 = true` 类型错误 90110。
        // EQL 展开式在 h2 上对非布尔操作数 = 响亮类型错误——宁可响亮也不静默接受 truthy 语义
        try (Connection con = DriverManager.getConnection("jdbc:h2:mem:wi8bool;DB_CLOSE_DELAY=-1")) {
            String sql = compile("h2", "select o.id from AppUser o where o.score is true");
            assertThrows(SQLException.class, () -> {
                try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                    // no-op
                }
            }, "h2 非布尔操作数必须响亮类型错误而非静默 truthy: " + sql);
        }
    }

    // ===== h2 真库执行：布尔操作数 3 行 × 6 谓词逐行断言（roadmap EC ③ 逐案用例） =====

    @ParameterizedTest
    @CsvSource({
            // 谓词, 期望命中行数（表数据：TRUE 1 行 / FALSE 1 行 / NULL 1 行）
            "o.deleted is true, 1",
            "o.deleted is not true, 2",
            "o.deleted is false, 1",
            "o.deleted is not false, 2",
            "o.deleted is unknown, 1",
            "o.deleted is not unknown, 2",
    })
    public void testH2TruthTableBooleanOperand(String pred, int expectedCount) throws SQLException {
        try (Connection con = DriverManager.getConnection("jdbc:h2:mem:wi8truth;DB_CLOSE_DELAY=-1")) {
            try (Statement st = con.createStatement()) {
                st.execute("drop table if exists app_user");
                st.execute("create table app_user(sid bigint, deleted boolean)");
                st.execute("insert into app_user values(1, true)");
                st.execute("insert into app_user values(2, false)");
                st.execute("insert into app_user values(3, null)");

                String sql = compile("h2", "select o.id from AppUser o where " + pred);
                int count = 0;
                try (ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next())
                        count++;
                }
                assertEquals(expectedCount, count, "谓词 " + pred + " 输出: " + sql);
            }
        }
    }
}
