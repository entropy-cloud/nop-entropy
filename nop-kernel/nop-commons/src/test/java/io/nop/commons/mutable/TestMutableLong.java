/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.mutable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMutableLong {

    @Test
    public void testConstructorsAndConversions() {
        assertEquals(0L, new MutableLong().get());
        assertEquals(5L, new MutableLong(5L).get());
        assertEquals(7L, new MutableLong(Integer.valueOf(7)).get());
        assertEquals(9L, new MutableLong("9").get());

        MutableLong v = new MutableLong(300);
        assertEquals(300, v.intValue());
        assertEquals(300L, v.longValue());
        assertEquals(300.0f, v.floatValue());
        assertEquals(300.0, v.doubleValue());
        assertEquals(Integer.valueOf(300), v.toInteger());
        assertEquals(Long.valueOf(300), v.toLong());
    }

    @Test
    public void testSetAndGetAndSwap() {
        MutableLong v = new MutableLong();
        v.set(10);
        assertEquals(10L, v.get());
        v.setValue(Long.valueOf(20));
        assertEquals(20L, v.getValue());

        // getAndSet 返回旧值
        assertEquals(20L, v.getAndSet(30));
        assertEquals(30L, v.get());

        // compareAndSet：期望匹配才更新
        assertTrue(v.compareAndSet(30, 40));
        assertEquals(40L, v.get());
        assertFalse(v.compareAndSet(30, 50));
        assertEquals(40L, v.get());
    }

    @Test
    public void testIncrementDecrementArithmetic() {
        MutableLong v = new MutableLong(10);
        assertEquals(10L, v.getAndIncrement());
        assertEquals(11L, v.get());
        assertEquals(12L, v.incrementAndGet());

        assertEquals(12L, v.getAndDecrement());
        assertEquals(11L, v.get());
        assertEquals(10L, v.decrementAndGet());

        assertEquals(15L, v.addAndGet(5));
        assertEquals(15L, v.getAndAdd(5));
        assertEquals(20L, v.get());
    }

    @Test
    public void testEqualsHashCodeCompareTo() {
        MutableLong a = new MutableLong(5);
        MutableLong b = new MutableLong(5);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(a.compareTo(b) == 0);
        assertTrue(a.equals(a));

        MutableLong bigger = new MutableLong(6);
        assertTrue(a.compareTo(bigger) < 0);
        assertTrue(bigger.compareTo(a) > 0);
        assertNotEquals(a, bigger);
        assertFalse(a.equals(Long.valueOf(5)));
    }

    @Test
    public void testOverflowBoundaries() {
        MutableLong v = new MutableLong(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, v.get());
        // long 溢出回绕是契约行为
        v.incrementAndGet();
        assertEquals(Long.MIN_VALUE, v.get());
    }

    @Test
    public void testToStringShowsValue() {
        assertEquals("5", new MutableLong(5).toString());
    }
}
