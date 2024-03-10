/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

import io.nop.core.lang.sql.SQL;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJdbcTemplate extends JdbcTestCase {
    @Test
    public void testFindFirst() {
        Object value = jdbc().findFirst(new SQL("select a from my_entity order by id"));
        assertEquals("3", value.toString());
    }

    @Test
    public void testFindAll() {
        List<Map<String, Object>> list = jdbc().findAll(new SQL("select a, b, c from my_entity order by id"));
        System.out.println(list);
        assertEquals("CC", list.get(0).get("C"));
    }

    @Test
    public void testFindPage() {
        List<Map<String, Object>> list = jdbc().findPage(new SQL("select a, b, c from my_entity order by id"), 0, 1);
        System.out.println(list);
        assertEquals("CC", list.get(0).get("C"));

        list = jdbc().findPage(new SQL("select a, b, c from my_entity order by id"), 1, 1);
        assertEquals(0, list.size());
    }

    @Test
    public void testExists() {
        assertTrue(jdbc().exists(new SQL("select * from my_entity")));
    }

    @Test
    public void testFindLong() {
        assertEquals(1, jdbc().findLong(new SQL("select count(*) from my_entity"), 0L));
    }

    @Test
    public void testExecuteStatement() {
        SQL sql = new SQL("update my_entity set a=3");
        long ret = jdbc().executeStatement(sql, null, ds -> {
            return ds.getUpdateCount();
        }, null);
        assertEquals(1, ret);

        String id = jdbc().executeStatement(new SQL("select id from my_entity"), null, ds -> {
            return ds.getResultSet().next().getString(0);
        }, null);
        assertEquals("1", id);
    }
}
