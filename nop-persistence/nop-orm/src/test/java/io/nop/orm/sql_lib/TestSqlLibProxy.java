/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql_lib;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.orm.AbstractJdbcTestCase;
import io.nop.orm.entity.MyEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSqlLibProxy extends AbstractJdbcTestCase {
    MyMapper mapper;

    @BeforeEach
    public void setUp() {
        super.setUp();
        mapper = sqlLibManager.createProxy(MyMapper.class);
    }

    @Test
    public void testInsert() {
        assertEquals(1, mapper.insert("x1", "1", 2, LocalDate.now()));
    }

    @Test
    public void testUpdate() {
        assertEquals(1, mapper.update("1", "1", "2"));
    }

    @Test
    public void testDelete() {
        mapper.equals("1");
        mapper.hashCode();
        assertEquals(1, mapper.delete("1"));
        assertEquals(0, mapper.deleteTest("1"));
    }

    @Test
    public void testSelect() {
        MyEntity entity = mapper.select("1");
        assertEquals(3, entity.getA());
        assertEquals(4, entity.getB());
        assertEquals("CC", entity.getC());
        assertTrue(entity.getMyDate() instanceof LocalDate);
    }

    @Test
    public void testSelectPage() {
        List<Map<String, Object>> list = mapper.selectPage(LongRangeBean.of(0, 10));
        System.out.println(JsonTool.stringify(list, null, "  "));
        Map<String, Object> map = list.get(0);
        assertEquals("x", map.get("a'b"));
        assertEquals("x", map.get("A'B"));
        assertEquals("y", map.get(" U`v"));
    }
}
