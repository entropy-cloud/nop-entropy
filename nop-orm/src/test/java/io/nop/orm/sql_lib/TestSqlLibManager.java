/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql_lib;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.orm.AbstractJdbcTestCase;
import io.nop.orm.entity.MyEntity;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSqlLibManager extends AbstractJdbcTestCase {

    @Test
    public void testInsert() {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, "id", "x1");
        scope.setLocalValue(null, "a", 1);
        scope.setLocalValue(null, "b", 2);
        scope.setLocalValue(null, "d", LocalDate.now());
        long count = (long) sqlLibManager.invoke("test.insert", null, scope);
        assertEquals(1, count);
    }

    @Test
    public void testUpdate() {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, "id", 1);
        scope.setLocalValue(null, "a", 1);
        scope.setLocalValue(null, "b", 2);
        long count = (long) sqlLibManager.invoke("test.update", null, scope);
        assertEquals(1, count);
    }

    @Test
    public void testDelete() {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, "id", 1);
        scope.setLocalValue(null, "a", 1);
        scope.setLocalValue(null, "b", 2);
        long count = (long) sqlLibManager.invoke("test.delete", null, scope);
        assertEquals(1, count);
    }

    @Test
    public void testSelect() {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, "id", 1);
        MyEntity entity = (MyEntity) sqlLibManager.invoke("test.select", null, scope);
        assertEquals(3, entity.getA());
        assertEquals(4, entity.getB());
        assertEquals("CC", entity.getC());
        assertTrue(entity.getMyDate() instanceof LocalDate);
    }

    @Test
    public void testSelectPage() {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, "id", 1);
        List<Map<String, Object>> list = (List<Map<String, Object>>) sqlLibManager.invoke("test.selectPage", null,
                scope);
        System.out.println(JsonTool.stringify(list, null, "  "));
        Map<String, Object> map = list.get(0);
        assertEquals("x", map.get("a'b"));
        assertEquals("x", map.get("A'B"));
        assertEquals("y", map.get(" U`v"));
    }
}