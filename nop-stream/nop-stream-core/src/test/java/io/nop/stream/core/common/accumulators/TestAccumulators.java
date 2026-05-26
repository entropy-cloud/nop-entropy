package io.nop.stream.core.common.accumulators;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

public class TestAccumulators {

    // ---- Counter accumulators ----

    @Test
    void testDoubleCounter_addAndGet() {
        DoubleCounter acc = new DoubleCounter();
        assertEquals(0.0, acc.getLocalValue());
        acc.add(1.5);
        acc.add(2.5);
        assertEquals(4.0, acc.getLocalValue());
    }

    @Test
    void testDoubleCounter_merge() {
        DoubleCounter a = new DoubleCounter();
        a.add(10.0);
        DoubleCounter b = new DoubleCounter();
        b.add(20.0);
        a.merge(b);
        assertEquals(30.0, a.getLocalValue());
    }

    @Test
    void testDoubleCounter_resetAndClone() {
        DoubleCounter acc = new DoubleCounter(5.0);
        DoubleCounter cloned = acc.clone();
        assertEquals(5.0, cloned.getLocalValue());
        acc.resetLocal();
        assertEquals(0.0, acc.getLocalValue());
        assertEquals(5.0, cloned.getLocalValue());
    }

    @Test
    void testIntCounter_addAndGet() {
        IntCounter acc = new IntCounter();
        assertEquals(0, acc.getLocalValue());
        acc.add(3);
        acc.add(7);
        assertEquals(10, acc.getLocalValue());
    }

    @Test
    void testIntCounter_merge() {
        IntCounter a = new IntCounter();
        a.add(5);
        IntCounter b = new IntCounter();
        b.add(15);
        a.merge(b);
        assertEquals(20, a.getLocalValue());
    }

    @Test
    void testIntCounter_resetAndClone() {
        IntCounter acc = new IntCounter();
        acc.add(42);
        IntCounter cloned = acc.clone();
        assertEquals(42, cloned.getLocalValue());
        acc.resetLocal();
        assertEquals(0, acc.getLocalValue());
    }

    @Test
    void testLongCounter_addAndGet() {
        LongCounter acc = new LongCounter();
        assertEquals(0L, acc.getLocalValue());
        acc.add(100L);
        acc.add(200L);
        assertEquals(300L, acc.getLocalValue());
    }

    @Test
    void testLongCounter_merge() {
        LongCounter a = new LongCounter();
        a.add(1000L);
        LongCounter b = new LongCounter();
        b.add(2000L);
        a.merge(b);
        assertEquals(3000L, a.getLocalValue());
    }

    @Test
    void testLongCounter_resetAndClone() {
        LongCounter acc = new LongCounter();
        acc.add(99L);
        LongCounter cloned = acc.clone();
        assertEquals(99L, cloned.getLocalValue());
        acc.resetLocal();
        assertEquals(0L, acc.getLocalValue());
    }

    // ---- Average accumulator ----

    @Test
    void testAverageAccumulator_basicAverage() {
        AverageAccumulator acc = new AverageAccumulator();
        assertEquals(0.0, acc.getLocalValue());
        acc.add(10.0);
        acc.add(20.0);
        acc.add(30.0);
        assertEquals(20.0, acc.getLocalValue(), 0.001);
    }

    @Test
    void testAverageAccumulator_mixedTypes() {
        AverageAccumulator acc = new AverageAccumulator();
        acc.add(10);   // int
        acc.add(20L);  // long
        acc.add(30.0); // double
        assertEquals(20.0, acc.getLocalValue(), 0.001);
    }

    @Test
    void testAverageAccumulator_merge() {
        AverageAccumulator a = new AverageAccumulator();
        a.add(10.0);
        a.add(20.0);
        AverageAccumulator b = new AverageAccumulator();
        b.add(30.0);
        a.merge(b);
        assertEquals(20.0, a.getLocalValue(), 0.001);
    }

    @Test
    void testAverageAccumulator_cloneAndReset() {
        AverageAccumulator acc = new AverageAccumulator();
        acc.add(100.0);
        AverageAccumulator cloned = acc.clone();
        assertEquals(100.0, cloned.getLocalValue(), 0.001);
        acc.resetLocal();
        assertEquals(0.0, acc.getLocalValue());
    }

    // ---- Maximum accumulators ----

    @Test
    void testIntMaximum_basic() {
        IntMaximum acc = new IntMaximum();
        assertEquals(Integer.MIN_VALUE, acc.getLocalValue());
        acc.add(5);
        acc.add(10);
        acc.add(3);
        assertEquals(10, acc.getLocalValue());
    }

    @Test
    void testIntMaximum_merge() {
        IntMaximum a = new IntMaximum();
        a.add(10);
        IntMaximum b = new IntMaximum();
        b.add(20);
        a.merge(b);
        assertEquals(20, a.getLocalValue());
    }

    @Test
    void testDoubleMaximum_basic() {
        DoubleMaximum acc = new DoubleMaximum();
        assertEquals(Double.NEGATIVE_INFINITY, acc.getLocalValue());
        acc.add(3.14);
        acc.add(2.71);
        assertEquals(3.14, acc.getLocalValue(), 0.001);
    }

    @Test
    void testLongMaximum_basic() {
        LongMaximum acc = new LongMaximum();
        assertEquals(Long.MIN_VALUE, acc.getLocalValue());
        acc.add(100L);
        acc.add(200L);
        assertEquals(200L, acc.getLocalValue());
    }

    // ---- Minimum accumulators ----

    @Test
    void testIntMinimum_basic() {
        IntMinimum acc = new IntMinimum();
        assertEquals(Integer.MAX_VALUE, acc.getLocalValue());
        acc.add(10);
        acc.add(3);
        acc.add(7);
        assertEquals(3, acc.getLocalValue());
    }

    @Test
    void testIntMinimum_merge() {
        IntMinimum a = new IntMinimum();
        a.add(10);
        IntMinimum b = new IntMinimum();
        b.add(5);
        a.merge(b);
        assertEquals(5, a.getLocalValue());
    }

    @Test
    void testDoubleMinimum_basic() {
        DoubleMinimum acc = new DoubleMinimum();
        assertEquals(Double.POSITIVE_INFINITY, acc.getLocalValue());
        acc.add(3.14);
        acc.add(1.41);
        assertEquals(1.41, acc.getLocalValue(), 0.001);
    }

    @Test
    void testLongMinimum_basic() {
        LongMinimum acc = new LongMinimum();
        assertEquals(Long.MAX_VALUE, acc.getLocalValue());
        acc.add(200L);
        acc.add(100L);
        assertEquals(100L, acc.getLocalValue());
    }

    // ---- Histogram ----

    @Test
    void testHistogram_basic() {
        Histogram acc = new Histogram();
        assertTrue(acc.getLocalValue().isEmpty());
        acc.add(1);
        acc.add(2);
        acc.add(1);
        acc.add(3);
        acc.add(1);
        TreeMap<Integer, Integer> result = acc.getLocalValue();
        assertEquals(3, result.get(1));
        assertEquals(1, result.get(2));
        assertEquals(1, result.get(3));
    }

    @Test
    void testHistogram_merge() {
        Histogram a = new Histogram();
        a.add(1);
        a.add(2);
        Histogram b = new Histogram();
        b.add(1);
        b.add(3);
        a.merge(b);
        TreeMap<Integer, Integer> result = a.getLocalValue();
        assertEquals(2, result.get(1));
        assertEquals(1, result.get(2));
        assertEquals(1, result.get(3));
    }

    @Test
    void testHistogram_resetAndClone() {
        Histogram acc = new Histogram();
        acc.add(1);
        acc.add(1);
        Histogram cloned = (Histogram) acc.clone();
        assertEquals(2, cloned.getLocalValue().get(1));
        acc.resetLocal();
        assertTrue(acc.getLocalValue().isEmpty());
        assertEquals(2, cloned.getLocalValue().get(1));
    }

    // ---- ListAccumulator ----

    @Test
    void testListAccumulator_basic() {
        ListAccumulator<String> acc = new ListAccumulator<>();
        assertTrue(acc.getLocalValue().isEmpty());
        acc.add("a");
        acc.add("b");
        acc.add("c");
        assertEquals(ArrayList.class, acc.getLocalValue().getClass());
        assertEquals(3, acc.getLocalValue().size());
        assertEquals("a", acc.getLocalValue().get(0));
    }

    @Test
    void testListAccumulator_merge() {
        ListAccumulator<Integer> a = new ListAccumulator<>();
        a.add(1);
        a.add(2);
        ListAccumulator<Integer> b = new ListAccumulator<>();
        b.add(3);
        b.add(4);
        a.merge(b);
        assertEquals(4, a.getLocalValue().size());
        assertEquals(4, a.getLocalValue().get(3));
    }

    // ---- LastValue ----

    @Test
    void testLastValue_basic() throws InterruptedException {
        LastValue<String> acc = new LastValue<>();
        assertNull(acc.getLocalValue());
        acc.add("first");
        assertEquals("first", acc.getLocalValue());
        acc.add("second");
        assertEquals("second", acc.getLocalValue());
    }

    @Test
    void testLastValue_resetAndClone() {
        LastValue<Integer> acc = new LastValue<>();
        acc.add(42);
        LastValue<Integer> cloned = (LastValue<Integer>) acc.clone();
        assertEquals(42, cloned.getLocalValue());
        acc.resetLocal();
        assertNull(acc.getLocalValue());
        assertEquals(42, cloned.getLocalValue());
    }

    // ---- Cross-cutting: all counters handle negative values ----

    @Test
    void testDoubleCounter_negativeValues() {
        DoubleCounter acc = new DoubleCounter();
        acc.add(-5.0);
        acc.add(3.0);
        assertEquals(-2.0, acc.getLocalValue());
    }

    @Test
    void testIntCounter_negativeValues() {
        IntCounter acc = new IntCounter();
        acc.add(-5);
        acc.add(3);
        assertEquals(-2, acc.getLocalValue());
    }

    @Test
    void testLongCounter_negativeValues() {
        LongCounter acc = new LongCounter();
        acc.add(-5L);
        acc.add(3L);
        assertEquals(-2L, acc.getLocalValue());
    }
}
