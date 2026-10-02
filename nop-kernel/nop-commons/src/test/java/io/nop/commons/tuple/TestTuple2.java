/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.tuple;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTuple2 {

    @Test
    public void testFieldAccessByPosition() {
        Tuple2<String, Integer> tuple = Tuple2.of("a", 1);
        assertEquals(2, tuple.getArity());
        assertEquals("a", tuple.<String>getField(0));
        assertEquals(1, tuple.<Integer>getField(1));

        tuple.setField("b", 0);
        tuple.setField(2, 1);
        assertEquals("b", tuple.f0);
        assertEquals(2, tuple.f1);

        assertThrows(IndexOutOfBoundsException.class, () -> tuple.getField(2));
        assertThrows(IndexOutOfBoundsException.class, () -> tuple.getField(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> tuple.setField("x", 2));
    }

    @Test
    public void testNullFieldsAndSetFields() {
        Tuple2<String, Integer> tuple = new Tuple2<>();
        assertNull(tuple.f0);
        assertNull(tuple.f1);
        assertEquals(2, tuple.getArity());

        tuple.setFields("x", 9);
        assertEquals("x", tuple.f0);
        assertEquals(9, tuple.f1);
    }

    @Test
    public void testSwapReturnsNewTupleWithSwappedFields() {
        Tuple2<String, Integer> tuple = Tuple2.of("a", 1);
        Tuple2<Integer, String> swapped = tuple.swap();
        assertEquals(1, swapped.f0);
        assertEquals("a", swapped.f1);
        // swap 是浅拷贝，原元组不变
        assertEquals("a", tuple.f0);
        assertEquals(1, tuple.f1);
    }

    @Test
    public void testCopyIsShallowIndependent() {
        Tuple2<String, Integer> tuple = Tuple2.of("a", 1);
        Tuple2<String, Integer> copy = tuple.copy();
        assertEquals(tuple, copy);
        copy.f0 = "changed";
        assertEquals("a", tuple.f0);
        assertNotEquals(tuple, copy);
    }

    @Test
    public void testEqualsAndHashCodeContract() {
        Tuple2<String, Integer> a = Tuple2.of("a", 1);
        Tuple2<String, Integer> b = Tuple2.of("a", 1);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        assertTrue(a.equals(a));
        assertFalse(a.equals(null));
        assertFalse(a.equals("not-a-tuple"));

        // 逐字段差异均导致不等
        assertNotEquals(a, Tuple2.of("x", 1));
        assertNotEquals(a, Tuple2.of("a", 2));

        // null 字段参与 equals/hashCode
        Tuple2<String, Integer> n1 = new Tuple2<>(null, null);
        Tuple2<String, Integer> n2 = new Tuple2<>(null, null);
        assertEquals(n1, n2);
        assertEquals(n1.hashCode(), n2.hashCode());
        assertNotEquals(n1, a);
    }

    @Test
    public void testToStringFormat() {
        assertEquals("(a,1)", Tuple2.of("a", 1).toString());
        // null 字段输出字面 null
        assertEquals("(null,null)", new Tuple2<>().toString());
    }
}
