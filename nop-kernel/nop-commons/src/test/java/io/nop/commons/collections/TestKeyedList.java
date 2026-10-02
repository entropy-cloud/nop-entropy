/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.CommonErrors;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestKeyedList {

    static class Item {
        final String name;
        final int order;

        Item(String name, int order) {
            this.name = name;
            this.order = order;
        }

        public String getName() {
            return name;
        }

        public int getOrder() {
            return order;
        }

        public String toString() {
            return name;
        }
    }

    private KeyedList<Item> newList(Item... items) {
        KeyedList<Item> list = new KeyedList<>(Item::getName);
        for (Item item : items)
            list.add(item);
        return list;
    }

    @Test
    public void testAddAndGetByKey() {
        KeyedList<Item> list = newList(new Item("a", 1), new Item("b", 2));
        assertEquals(2, list.size());
        assertEquals("a", list.getByKey("a").name);
        assertNull(list.getByKey("missing"));
        assertTrue(list.containsKey("a"));
        assertFalse(list.containsKey("z"));
        assertEquals(java.util.Set.of("a", "b"), list.keySet());
        // indexOf 是 List 视图语义：按元素（实例相等）而非键查找
        assertEquals(0, list.indexOf(list.getByKey("a")));
        assertEquals(-1, list.indexOf("a"));

        // List 视图按插入顺序
        assertEquals("a", list.get(0).name);
        assertEquals("b", list.get(1).name);
    }

    @Test
    public void testAddNullRejected() {
        KeyedList<Item> list = new KeyedList<>(Item::getName);
        assertThrows(NopException.class, () -> list.add(null));
        // null 元素在调用 errorFactory 之前即被拒绝
        assertThrows(NopException.class,
                () -> list.addUnique(null,
                        k -> new NopException(CommonErrors.ERR_LIST_NOT_ALLOW__DUPLICATE_KEY)));
    }

    @Test
    public void testAddDuplicateKeyReplacesInPlace() {
        KeyedList<Item> list = new KeyedList<>(Item::getName);
        assertTrue(list.add(new Item("a", 1)));
        assertTrue(list.add(new Item("b", 2)));

        // 同 key 新实例：原位置替换，size 不变，返回 true
        Item replacement = new Item("a", 10);
        assertTrue(list.add(replacement));
        assertEquals(2, list.size());
        assertSame(replacement, list.get(0));
        assertSame(replacement, list.getByKey("a"));

        // 同一实例重复 add：不产生变化，返回 false
        assertFalse(list.add(replacement));
        assertEquals(2, list.size());
    }

    @Test
    public void testAddUniqueThrowsOnDuplicate() {
        KeyedList<Item> list = new KeyedList<>(Item::getName);
        list.addUnique(new Item("a", 1),
                k -> new NopException(CommonErrors.ERR_LIST_NOT_ALLOW__DUPLICATE_KEY).param(CommonErrors.ARG_KEY, k));
        NopException e = assertThrows(NopException.class,
                () -> list.addUnique(new Item("a", 2),
                        k -> new NopException(CommonErrors.ERR_LIST_NOT_ALLOW__DUPLICATE_KEY)
                                .param(CommonErrors.ARG_KEY, k)));
        assertEquals("nop.err.commons.list-not-allow-duplicate-key", e.getErrorCode());
        // 失败路径不改变集合
        assertEquals(1, list.size());
    }

    @Test
    public void testFromListRejectsDuplicateKeys() {
        List<Item> items = Arrays.asList(new Item("a", 1), new Item("a", 2));
        assertThrows(NopException.class, () -> KeyedList.fromList(items, Item::getName));

        // null 输入得到冻结空表
        assertTrue(KeyedList.fromList(null, Item::getName).isEmpty());
        assertTrue(KeyedList.fromList(null, Item::getName).frozen());
        // KeyedList 输入原样返回
        KeyedList<Item> existing = newList(new Item("a", 1));
        assertSame(existing, KeyedList.fromList(existing, Item::getName));
    }

    @Test
    public void testRemoveByKeyRemovesFromBothViewAndIndex() {
        Item a = new Item("a", 1);
        KeyedList<Item> list = newList(a, new Item("b", 2), new Item("c", 3));
        assertEquals("b", list.removeByKey("b").name);
        assertEquals(2, list.size());
        assertNull(list.getByKey("b"));
        assertNull(list.removeByKey("missing"));
        // contains 基于键索引 + 实例相等
        assertTrue(list.contains(a));
        assertFalse(list.contains(new Item("b", 2)));
        assertFalse(list.contains(new Item("a", 99)));
    }

    @Test
    public void testRemoveByIndexAndObject() {
        Item a = new Item("a", 1);
        Item b = new Item("b", 2);
        KeyedList<Item> list = newList(a, b);
        assertEquals("a", list.remove(0).name);
        assertNull(list.getByKey("a"));
        assertEquals(1, list.size());

        // remove(Object) 同一实例命中
        assertTrue(list.remove(b));
        assertEquals(0, list.size());
        assertFalse(list.remove(null));
        // 键存在但实例不同 → 不移除
        assertFalse(list.remove(new Item("missing", 9)));
    }

    @Test
    public void testSetReplacesIndexMapping() {
        KeyedList<Item> list = newList(new Item("a", 1), new Item("b", 2));
        Item replacement = new Item("c", 3);
        assertEquals("a", list.set(0, replacement).name);
        assertNull(list.getByKey("a"));
        assertSame(replacement, list.getByKey("c"));
        assertEquals(2, list.size());

        assertThrows(NopException.class, () -> list.set(0, null));
    }

    @Test
    public void testAddAtIndexReplacesOldKeyMapping() {
        KeyedList<Item> list = newList(new Item("a", 1));
        list.add(0, new Item("b", 2));
        assertEquals(2, list.size());
        assertEquals("b", list.get(0).name);
        assertEquals("a", list.get(1).name);
        assertSame(list.get(0), list.getByKey("b"));

        // 同 key 不同实例插入：旧实例从 list 中移除
        Item dup = new Item("a", 9);
        list.add(1, dup);
        assertEquals(2, list.size());
        assertSame(dup, list.getByKey("a"));
        assertFalse(list.contains(new Item("a", 1)));
    }

    @Test
    public void testClearAndCloneInstance() {
        KeyedList<Item> list = newList(new Item("a", 1), new Item("b", 2));
        KeyedList<Item> clone = list.cloneInstance();
        assertEquals(list, clone);
        clone.clear();
        assertEquals(0, clone.size());
        assertFalse(clone.containsKey("a"));
        // 克隆是深独立的
        assertEquals(2, list.size());

        list.clear();
        assertTrue(list.isEmpty());
        assertEquals(new ArrayList<>(), list);
    }

    @Test
    public void testSortOrdersByComparator() {
        KeyedList<Item> list = newList(new Item("b", 2), new Item("a", 1));
        list.sort(java.util.Comparator.comparingInt(Item::getOrder));
        assertEquals("a", list.get(0).name);
        assertEquals("b", list.get(1).name);
        // 键索引不因排序而失效
        assertSame(list.get(0), list.getByKey("a"));
    }

    @Test
    public void testFreezeBlocksMutation() {
        KeyedList<Item> list = newList(new Item("a", 1));
        assertFalse(list.frozen());
        list.freezeList();
        assertTrue(list.frozen());
        assertThrows(NopException.class, () -> list.add(new Item("b", 2)));
        assertThrows(NopException.class, () -> list.removeByKey("a"));
        assertThrows(NopException.class, list::clear);
        // 冻结表仍可读
        assertEquals("a", list.getByKey("a").name);

        // 全局空表是冻结的
        assertTrue(KeyedList.emptyList().frozen());
        assertThrows(NopException.class, () -> KeyedList.<Item>emptyList().add(new Item("x", 1)));
    }

    @Test
    public void testGetKeyNullSafe() {
        assertNull(newList().getKey(null));
        assertEquals("a", newList().getKey(new Item("a", 1)));
        assertEquals("[]", newList().toString());
        assertEquals("[a, b]", newList(new Item("a", 1), new Item("b", 2)).toString());
    }
}
