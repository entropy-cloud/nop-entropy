/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.loader;

import io.nop.app.SimsClass;
import io.nop.app.SimsCollege;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestEqlQuery extends AbstractOrmTestCase {
    @Test
    public void testSimpleSelect() {
        String sql = "select o from io.nop.app.SimsClass o";
        List<SimsClass> list = orm().findAll(new SQL(sql));
        assertEquals(1, list.size());
    }

    @Test
    public void testSimpleCondition() {
        orm().runInSession(() -> {
            String sql = "select o.id, o.classId, o.simsCollege, concat(o.className,'XX') "
                    + "from io.nop.app.SimsClass o where o.classId=?";

            List<Map<String, Object>> list = orm().findAll(SQL.begin().sql(sql, 11).end());
            assertEquals(1, list.size());
            Map<String, Object> row = list.get(0);
            System.out.println(row);
            assertEquals("11", row.get("id"));
            assertEquals("11", row.get("classId"));
            assertNull(row.get("classID")); // eql返回的字段名大小写敏感
            SimsCollege college = (SimsCollege) row.get("simsCollege");
            assertEquals("1", college.getCollegeId());
            assertEquals("CollegeA", college.getCollegeName());
        });
    }

    @Test
    public void testSimplePropJoin() {
        orm().runInSession(() -> {
            String sql = "select o.simsCollege.collegeName"
                    + " from io.nop.app.SimsClass o where o.classId=? order by o.simsCollege.intro";

            List<String> list = orm().findAll(SQL.begin().sql(sql, 11).end());
            assertEquals(1, list.size());
            assertEquals("CollegeA", list.get(0));
        });
    }

    @Test
    public void testLeftJoin() {
        orm().runInSession(() -> {
            String sql = "select o.simsCollege.collegeName"
                    + " from io.nop.app.SimsClass o left join o.simsCollege where o.classId=? order by o.simsCollege.intro";

            List<String> list = orm().findAll(SQL.begin().sql(sql, 11).end());
            assertEquals(1, list.size());
            assertEquals("CollegeA", list.get(0));
        });
    }

    @Test
    public void testGroupBy() {
        orm().runInSession(() -> {
            String sql = "select o.simsCollege.collegeName"
                    + " from io.nop.app.SimsClass o group by o.simsCollege.collegeName order by o.simsCollege.collegeName";

            List<String> list = orm().findAll(new SQL(sql));
            assertEquals(1, list.size());
            assertEquals("CollegeA", list.get(0));
        });
    }

    @Test
    public void testSubQueryFilter() {
        orm().runInSession(() -> {
            String sql = "select o.collegeName"
                    + " from io.nop.app.SimsCollege o where o.collegeId in (select o2.collegeId "
                    + "\n from io.nop.app.SimsClass o2 where o2.classId = ?)";

            List<String> list = orm().findAll(SQL.begin().sql(sql, "11").end());
            assertEquals(1, list.size());
            assertEquals("CollegeA", list.get(0));
        });
    }

    @Test
    public void testCommaJoin() {
        orm().runInSession(() -> {
            String sql = "select o.collegeName"
                    + " from io.nop.app.SimsCollege o, io.nop.app.SimsClass o2 where o.collegeId = o2.collegeId and o2.classId=?";

            List<String> list = orm().findAll(SQL.begin().sql(sql, "11").end());
            assertEquals(1, list.size());
            assertEquals("CollegeA", list.get(0));
        });
    }

    @Test
    public void testSelectStar() {
        orm().runInSession(() -> {
            String sql = "select o.*"
                    + " from io.nop.app.SimsCollege o, io.nop.app.SimsClass o2 where o.collegeId = o2.collegeId and o2.classId=?";

            List<Map<String, Object>> list = orm().findAll(SQL.begin().sql(sql, "11").end());
            System.out.println(list.get(0));
            assertEquals(1, list.size());
            assertEquals("CollegeA", list.get(0).get("collegeName"));
        });
    }

    @Test
    public void testSelectStar2() {
        orm().runInSession(() -> {
            String sql = "select *"
                    + " from io.nop.app.SimsCollege o, io.nop.app.SimsClass o2 where o.collegeId = o2.collegeId and o2.classId=?";

            List<Map<String, Object>> list = orm().findAll(SQL.begin().sql(sql, "11").end());
            System.out.println(list.get(0));
            assertEquals(1, list.size());
            assertEquals("CollegeA", list.get(0).get("collegeName"));
        });
    }

    @Test
    public void testExists() {
        orm().runInSession(() -> {
            String sql = "select o.*"
                    + " from io.nop.app.SimsCollege o where exists (select o2.classId from io.nop.app.SimsClass o2 "
                    + "where o2.collegeId = o.collegeId)";

            List<Map<String, Object>> list = orm().findAll(SQL.begin().sql(sql).end());
            System.out.println(list.get(0));
            assertEquals(1, list.size());
            assertEquals("CollegeA", list.get(0).get("collegeName"));
        });
    }

    @Test
    public void testSubQueryEq() {
        orm().runInSession(() -> {
            String sql = "@dump select o"
                    + " from io.nop.app.SimsCollege o where o.collegeId = (select o2.collegeId from io.nop.app.SimsClass o2 "
                    + "where o2.classId = ?)";

            List<SimsCollege> list = orm().findAll(SQL.begin().sql(sql, 11).end());
            assertEquals(1, list.size());
            assertEquals("CollegeA", list.get(0).getCollegeName());
        });
    }

    @Test
    public void testSubQuerySource() {
        orm().runInSession(() -> {
            String sql = "@dump select o.collegeName as 'table', o.cnt"
                    + " from (select o2.collegeName, count(distinct o2.collegeId) cnt, sum(1) from io.nop.app.SimsCollege o2 group by o2.collegeName) o"
                    + " where o.collegeName = 'CollegeA' ";

            List<Map<String, Object>> list = orm().findAll(SQL.begin().sql(sql).end());
            assertEquals(1, list.size());
            System.out.println(list.get(0));
            assertEquals("CollegeA", list.get(0).get("table"));
        });
    }

    @Test
    public void testColumnName() {
        orm().runInSession(() -> {
            String sql = "@dump select collegeName,intro " + " from io.nop.app.SimsCollege";

            List<Map<String, Object>> list = orm().findAll(SQL.begin().sql(sql).end());
            assertEquals(1, list.size());
            System.out.println(list.get(0));
            assertEquals("CollegeA", list.get(0).get("collegeName"));
        });
    }

    @Test
    public void testSelectFromDual() {
        orm().runInSession(() -> {
            String sql = "select DATE'2002-01-02' as d, TIME'10:01:05' as t, TIMESTAMP '2002-01-02 22:00:01' as ts, "
                    + "?+1 as 'sum', now() as now";

            Map<String, Object> map = orm().findFirst(SQL.begin().sql(sql, 1).end());
            System.out.println(map);
            assertEquals(LocalDate.of(2002, 01, 02), map.get("d"));
            assertEquals(LocalTime.of(10, 01, 05), map.get("t"));
            Timestamp ts = (Timestamp) map.get("ts");
            assertEquals("2002-01-02T22:00:01", ts.toLocalDateTime().toString());
            assertEquals(new BigDecimal(2), map.get("sum"));
        });
    }

    @Test
    public void testLimit() {
        orm().runInSession(() -> {
            String sql = "select o.simsCollege from io.nop.app.SimsClass o where o.classId=? limit 1 offset ?";

            List<SimsCollege> list = orm().findAll(SQL.begin().sql(sql, 11, 0).end());
            assertEquals(1, list.size());
            assertEquals("CollegeA", list.get(0).getCollegeName());
        });
    }

    @Test
    public void testCast() {
        orm().runInSession(() -> {
            String sql = "select cast('1' as INTEGER)";
            assertEquals(Integer.valueOf(1), orm().findFirst(new SQL(sql)));
        });
    }

    @Test
    public void testCase() {
        orm().runInSession(() -> {
            String sql = "select CASE WHEN o.sex = '1' THEN '男'\n" + "WHEN o.sex = '0' THEN '女'\n"
                    + "ELSE '其他' END as result from (select '1' as sex ) o ";
            assertEquals("男", orm().findFirst(new SQL(sql)));
        });
    }

    @Test
    public void testComplexQuery() {
        orm().runInSession(() -> {
            String sql = "@dump with a as (select o.className, o.simsCollege from io.nop.app.SimsClass o) "
                    + "\n select a.*, a.className from a where a.simsCollege = 3";
            orm().findFirst(new SQL(sql));
        });
    }

    @Test
    public void testLeftJoinOn() {
        String sql = "select o from io.nop.app.SimsClass o left join io.nop.app.SimsCollege c1 on o.collegeId = c1.collegeId";
        List<SimsClass> list = orm().findAll(new SQL(sql));
        assertEquals(1, list.size());


        String sql2 = "select o from SimsClass o left join SimsCollege c1 on o.collegeId = c1.collegeId " +
                " left join SimsCollege c2 on c1.collegeId = c2.collegeId where c1.collegeId is not null or c2.collegeId is not null";
        List<SimsClass> list2 = orm().findAll(new SQL(sql2));
        assertEquals(1, list2.size());
    }
}