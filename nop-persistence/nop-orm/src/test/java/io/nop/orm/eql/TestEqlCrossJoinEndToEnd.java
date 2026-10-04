/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2307 Phase 3 / V2：<code>CROSS JOIN</code> 的端到端验证（H2 实跑）。
 *
 * <p>缺陷背景：<code>cross</code> 被吃成左表别名后，源查询里对原别名的引用报
 * <code>unknown-column-name</code>——一条本应合法的 CROSS JOIN 查询无法执行。
 * 本类证明修复后它不仅能编译，还能在数据库上跑出正确的笛卡尔积行数。
 */
public class TestEqlCrossJoinEndToEnd extends AbstractOrmTestCase {

    SQL compile(String sql) {
        return orm().getSessionFactory()
                .compileSql("test", sql, true, null, true, false, true)
                .getSql();
    }

    /** CROSS JOIN 两侧各取一个实体；修复前 cross 被吃成别名，此查询直接编译失败 */
    static final String CROSS_JOIN_SQL =
            "select c.id as collegeId, s.classId as classId "
                    + "from SimsCollege c cross join SimsClass s";

    @Test
    public void testCrossJoinSqlContainsKeyword() {
        SQL sql = compile(CROSS_JOIN_SQL);
        String text = sql.getText().replaceAll("\\s+", " ").trim();
        assertTrue(text.contains("cross join"), "编译产物必须含 cross join: " + text);
    }

    @Test
    public void testCrossJoinQueryRunsOnH2() {
        List<Map<String, Object>> rows = orm().findAll(SQL.begin().sql(CROSS_JOIN_SQL).end());
        assertFalse(rows.isEmpty(), "笛卡尔积不应为空");
    }

    @Test
    public void testCrossJoinProducesCartesianProduct() {
        // 行数必须等于两侧行数之积——这正是 CROSS JOIN 与 INNER JOIN 的区别，
        // 也是「静默降级成普通 join」最容易被忽略的语义后果。
        // 用 findAll().size() 而非 findLong()：后者只读第一行第 0 列，拿到的是 id 而非行数。
        long collegeCount = orm().findAll(SQL.begin().sql("select o.id from SimsCollege o").end()).size();
        long classCount = orm().findAll(SQL.begin().sql("select o.id from SimsClass o").end()).size();
        assertTrue(collegeCount > 0 && classCount > 0, "两侧都必须有数据");

        long crossCount = orm().findAll(SQL.begin().sql(CROSS_JOIN_SQL).end()).size();
        assertEquals(collegeCount * classCount, crossCount,
                "CROSS JOIN 行数必须等于两侧行数之积");
    }

    @Test
    public void testCrossJoinWithLeftAliasKeepsAliasUsable() {
        // 左表带别名时 cross 由 grammar 的 crossJoin_ 规则匹配（另一条代码路径）
        String eql = "select c.id as collegeId from SimsCollege c cross join SimsClass s "
                + "where c.collegeId = s.collegeId";
        SQL sql = compile(eql);
        assertTrue(sql.getText().replaceAll("\\s+", " ").contains("cross join"), sql.getText());
        assertFalse(orm().findAll(SQL.begin().sql(eql).end()).isEmpty());
    }

    @Test
    public void testCrossAsColumnNameStillParses() {
        // 防误伤：cross 仍登记为非保留字，故名为 cross 的标识符在词法层仍可解析
        // （SimsClass 没有名为 cross 的列，故止于语义解析而非词法解析）
        NopException e = assertThrows(NopException.class, () ->
                orm().findAll(SQL.begin().sql("select cross from SimsClass o").end()));
        assertEquals("nop.err.eql.unknown-column-name", e.getErrorCode(),
                "cross 应已可作列名，失败点应在语义解析而非词法: " + e.getMessage());
    }

    @Test
    public void testCrossJoinAppliesTenantFilterToBothSides() {
        // 回归钉：V2b 的过滤条件改道 WHERE 时，曾因提前置位 filterAlreadyAdded 导致
        // addEntityFilter 整体跳过、产物退化为 `where 1=1`（租户条件全丢），
        // CROSS JOIN 变成跨租户笛卡尔积。此断言确保两侧租户条件都真实出现在 SQL 中。
        String text = compile(CROSS_JOIN_SQL).getText().replaceAll("\\s+", " ").trim();
        assertFalse(text.contains("cross join SIMS_CLASS as s on"),
                "CROSS JOIN 之后不得出现 on 子句: " + text);
        assertTrue(text.contains("TENANT_ID"), "租户过滤条件必须出现: " + text);
        // 两侧各一个条件
        int tenantConds = text.split("TENANT_ID", -1).length - 1;
        assertEquals(2, tenantConds, "CROSS JOIN 两侧都必须带租户条件: " + text);
    }

    @Test
    public void testCrossJoinRowCountMatchesFilteredSides() {
        // 租户过滤后两侧行数之积 == CROSS JOIN 结果行数（过滤确实生效而非被丢弃）
        long collegeCount = orm().findAll(SQL.begin().sql("select o.id from SimsCollege o").end()).size();
        long classCount = orm().findAll(SQL.begin().sql("select o.id from SimsClass o").end()).size();
        long crossCount = orm().findAll(SQL.begin().sql(CROSS_JOIN_SQL).end()).size();
        assertEquals(collegeCount * classCount, crossCount);
    }

    @Test
    public void testInnerJoinWithoutConditionStillRejected() {
        // 防误伤：普通 join 漏写 on 仍必须报错——CROSS JOIN 的放行不得放宽该校验
        NopException e = assertThrows(NopException.class, () ->
                orm().findAll(SQL.begin().sql("select c.id from SimsCollege c join SimsClass s").end()));
        assertEquals("nop.err.eql.join-no-condition", e.getErrorCode());
    }

    @Test
    public void testLeftJoinWithoutConditionStillRejected() {
        NopException e = assertThrows(NopException.class, () ->
                orm().findAll(SQL.begin()
                        .sql("select c.id from SimsCollege c left join SimsClass s").end()));
        assertEquals("nop.err.eql.join-no-condition", e.getErrorCode());
    }
}