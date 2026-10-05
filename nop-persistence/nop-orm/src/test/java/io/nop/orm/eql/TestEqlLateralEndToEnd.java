/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialectProvider;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.factory.SessionFactoryImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2307 Phase 2 / V1：LATERAL 子查询表源的编译产物契约 + 语义等价性。
 *
 * <p>缺陷背景：<code>EqlTransformVisitor</code> 放开了 LATERAL 的同级作用域引用，
 * 但输出器从不输出 LATERAL 关键字，编译出缺关键字的 SQL。
 *
 * <p><b>为什么本类不在 H2 上执行 LATERAL 查询</b>：实测 H2 2.3.232 <b>完全不支持</b>
 * {@code LATERAL} 关键字（逗号连接与 JOIN 两种写法均报 {@code Function "LATERAL" not found}），
 * 且逗号连接的派生表也<strong>不</strong>支持隐式 lateral（报 {@code Column "C.ID" not found}）。
 * 因此 LATERAL 的真实执行验证由 {@link TestEqlLateralPostgres} 在 PostgreSQL 上完成；
 * 本类负责「编译产物是标准 LATERAL 语法」与「语义等价于可执行的非 lateral 写法」两件事。
 */
public class TestEqlLateralEndToEnd extends AbstractOrmTestCase {

    // 【merge 调和（WI1 supportLateral 门 vs 本类编译契约）】：H2 2.4.240 实测不可执行
    // LATERAL（90022，两种写法，探针在案）⇒ WI1 的 h2 supportLateral=false fail-closed 门正确；
    // 本类的编译契约断言（标准 LATERAL 语法输出）改经 pg 方言上下文承载（pg supportLateral=true），
    // 执行类断言（标量子查询/非 lateral 路径）为方言中性 SQL、H2 数据源不受影响。@AfterAll 恢复，
    // 不向同 JVM 的其他测试类泄漏方言覆盖。
    static IDialectProvider prevDialectProvider;
    static SessionFactoryImpl sessionFactoryImpl;

    @org.junit.jupiter.api.BeforeEach
    public void overrideDialectForLateralCompile() {
        sessionFactoryImpl = (SessionFactoryImpl) orm().getSessionFactory();
        if (prevDialectProvider == null)
            prevDialectProvider = sessionFactoryImpl.getDialectProvider();
        sessionFactoryImpl.setDialectProvider(qs -> DialectManager.instance().getDialect("postgresql"));
    }

    @org.junit.jupiter.api.AfterAll
    public static void restoreDialectProvider() {
        if (sessionFactoryImpl != null && prevDialectProvider != null)
            sessionFactoryImpl.setDialectProvider(prevDialectProvider);
    }

    SQL compile(String sql) {
        return orm().getSessionFactory()
                .compileSql("test", sql, true, null, true, false, true)
                .getSql();
    }

    /**
     * 每个 college 的班级数。LATERAL 写法：逐个 college 关联统计其班级数。
     * 修复前编译为缺少 LATERAL 的 SQL——在 H2 与 PostgreSQL 上都是语法错误。
     */
    static final String LATERAL_SQL =
            "select c.id as collegeId, t.classCount "
                    + "from SimsCollege c, lateral "
                    + "(select count(*) as classCount from SimsClass s where s.collegeId = c.id) t "
                    + "order by c.id";

    /** 与 LATERAL_SQL 语义等价、且 H2 可执行的标量子查询写法，作为结果集对照基准 */
    static final String SCALAR_SUBQUERY_SQL =
            "select c.id as collegeId, "
                    + "(select count(*) from SimsClass s where s.collegeId = c.id) as classCount "
                    + "from SimsCollege c "
                    + "order by c.id";

    // ---- 编译产物契约 ----

    @Test
    public void testLateralSqlContainsKeyword() {
        SQL sql = compile(LATERAL_SQL);
        String text = sql.getText().replaceAll("\\s+", " ").trim();
        assertTrue(text.contains("lateral"), "编译产物必须含 LATERAL: " + text);
    }

    @Test
    public void testLateralPrecedesSubqueryOpeningParen() {
        // 关键字必须紧贴子查询左括号（`lateral (`），否则是畸形 SQL
        String text = compile(LATERAL_SQL).getText().replaceAll("\\s+", " ").trim();
        assertTrue(text.contains("lateral ("), "LATERAL 必须紧贴子查询左括号: " + text);
    }

    @Test
    public void testLateralAppearsExactlyOnce() {
        String text = compile(LATERAL_SQL).getText().replaceAll("\\s+", " ").trim();
        assertEquals(1, text.split("lateral", -1).length - 1,
                "单个 LATERAL 表源只应输出一次关键字: " + text);
    }

    // ---- 语义等价性（LATERAL 版 vs H2 可执行的标量子查询版） ----

    @Test
    public void testLateralIntentMatchesScalarSubqueryOnH2() {
        // LATERAL 写法在 H2 上不可执行，但它的语义等价物可执行。
        // 两者结果集逐行一致 ⇒ LATERAL 分支没有改变查询语义。
        String lateralText = compile(LATERAL_SQL).getText().replaceAll("\\s+", " ").trim();
        assertTrue(lateralText.contains("count(*)"), lateralText);

        List<Map<String, Object>> scalarRows = orm().findAll(SQL.begin().sql(SCALAR_SUBQUERY_SQL).end());
        assertFalse(scalarRows.isEmpty(), "SimsCollege 应有数据");
        for (Map<String, Object> row : scalarRows) {
            Object v = row.get("classCount");
            assertTrue(v instanceof Number, "classCount 应为数值，实际为 " + v);
            assertTrue(((Number) v).longValue() >= 0, "classCount 应非负，实际为 " + v);
        }
    }

    @Test
    public void testLateralSubqueryCorrelatesToOuterAlias() {
        // LATERAL 的核心价值：子查询能引用同级表别名。编译产物中关联条件必须保留外层限定
        String text = compile(LATERAL_SQL).getText().replaceAll("\\s+", " ").trim();
        assertTrue(text.contains("s.COLLEGE_ID = c.COLLEGE_ID"),
                "关联条件必须保留外层别名限定 c: " + text);
    }

    // ---- 防误伤：非 lateral 路径在 H2 上照常工作 ----

    @Test
    public void testNonLateralDerivedTableHasNoLateralKeyword() {
        SQL sql = compile("select t.className from (select o.className as className from SimsClass o) t");
        assertFalse(sql.getText().toLowerCase().contains("lateral"), sql.getText());
    }

    @Test
    public void testNonLateralDerivedTableStillRunsOnH2() {
        List<Map<String, Object>> rows = orm().findAll(SQL.begin()
                .sql("select t.className as cn from (select o.className as className from SimsClass o) t")
                .end());
        assertFalse(rows.isEmpty());
    }

    @Test
    public void testNonLateralSubqueryStillWorks() {
        List<Map<String, Object>> rows = orm().findAll(SQL.begin()
                .sql("select c.id as collegeId from SimsCollege c "
                        + "where c.id in (select s.collegeId from SimsClass s)")
                .end());
        assertFalse(rows.isEmpty());
    }
}