/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.graalvm;

import io.nop.commons.collections.KeyedList;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReflectConfig {

    private static ReflectClass clazz(String name, String... fieldNames) {
        ReflectClass clazz = new ReflectClass();
        clazz.setName(name);
        for (String fieldName : fieldNames) {
            ReflectField field = new ReflectField();
            field.setName(fieldName);
            clazz.addField(field);
        }
        return clazz;
    }

    private static KeyedList<ReflectClass> classes(ReflectConfig config) {
        return (KeyedList<ReflectClass>) config.getClassList();
    }

    @Test
    public void testAddAndContainsClass() {
        ReflectConfig config = new ReflectConfig();
        assertTrue(config.getClassList().isEmpty());
        config.addClass(clazz("A"));
        config.addClass(clazz("B"));
        assertTrue(config.containsClass("A"));
        assertFalse(config.containsClass("C"));
        assertEquals(2, config.getClassList().size());
    }

    @Test
    public void testMergeClassMergesIntoExistingOrAdds() {
        ReflectConfig config = new ReflectConfig();
        config.addClass(clazz("A", "x"));
        config.mergeClass(clazz("A", "y"));

        // mergeClass：同名类合并为一条，字段按名称去重合并
        assertEquals(1, config.getClassList().size());
        assertNotNull(classes(config).getByKey("A"));
        assertEquals(2, classes(config).getByKey("A").getFields().size());

        // addClass：同 key 是整体替换而非合并
        config.addClass(clazz("A", "only"));
        assertEquals(1, config.getClassList().size());
        assertEquals(1, classes(config).getByKey("A").getFields().size());

        config.mergeClass(clazz("B"));
        assertEquals(2, config.getClassList().size());
    }

    @Test
    public void testSortOrdersClassesAndNestedMembers() {
        ReflectConfig config = new ReflectConfig();
        config.addClass(clazz("b", "z", "a"));
        config.addClass(clazz("a", "y"));

        config.sort();

        assertEquals("a", config.getClassList().get(0).getName());
        assertEquals("b", config.getClassList().get(1).getName());
        // 嵌套成员同步排序
        assertEquals("y", config.getClassList().get(0).getFields().get(0).getName());
        assertEquals("a", config.getClassList().get(1).getFields().get(0).getName());
        assertEquals("z", config.getClassList().get(1).getFields().get(1).getName());
    }

    @Test
    public void testMergeFromOtherConfig() {
        ReflectConfig base = new ReflectConfig();
        base.addClass(clazz("A", "x"));

        ReflectConfig other = new ReflectConfig();
        other.addClass(clazz("A", "w"));
        other.addClass(clazz("B"));

        base.merge(other);
        assertEquals(2, base.getClassList().size());
        assertEquals(2, classes(base).getByKey("A").getFields().size());
        assertTrue(base.containsClass("B"));
    }

    @Test
    public void testRemoveDropsFieldsAndEmptiedClasses() {
        ReflectConfig base = new ReflectConfig();
        base.addClass(clazz("A", "x", "y"));
        base.addClass(clazz("B", "keep"));

        ReflectConfig delta = new ReflectConfig();
        delta.addClass(clazz("A", "x", "y"));
        // delta 的 allPublic* 等标志默认 true，会同时扣除 A 的反射标志
        base.remove(delta);

        // A 被清空后整类移除；B 不受影响
        assertEquals(1, base.getClassList().size());
        assertFalse(base.containsClass("A"));
        assertTrue(base.containsClass("B"));
        assertEquals("keep", classes(base).getByKey("B").getFields().get(0).getName());

        // remove 不存在的类是无操作
        base.remove(delta);
        assertEquals(1, base.getClassList().size());
    }

    @Test
    public void testSetClassListReplacesDuplicateByName() {
        ReflectConfig config = new ReflectConfig();
        // setClassList 底层走 KeyedList.add：同 key 后者整体替换前者
        config.setClassList(Arrays.asList(clazz("A", "x"), clazz("A", "y"), clazz("B")));
        assertEquals(2, config.getClassList().size());
        assertEquals("y", classes(config).getByKey("A").getFields().get(0).getName());
        assertNull(classes(config).getByKey("C"));
    }
}
