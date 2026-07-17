package io.nop.table.validator;

import io.nop.table.validator.validate.ColumnStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestColumnStats {

    @Test
    public void testEmptyStats() {
        ColumnStats stats = new ColumnStats();
        assertEquals(0, stats.getCount());
        assertEquals(0, stats.getNullCount());
        assertNull(stats.getMean());
        assertNull(stats.getMin());
        assertNull(stats.getMax());
        assertNull(stats.getSum());
        assertNull(stats.getSumOfSquares());
        assertNull(stats.getStdDev());
        assertEquals(0, stats.getDistinctCount());
    }

    @Test
    public void testSingleValue() {
        ColumnStats stats = new ColumnStats();
        stats.accumulate(42.0);
        assertEquals(1, stats.getCount());
        assertEquals(42.0, stats.getMean(), 1e-9);
        assertEquals(42.0, stats.getMin(), 1e-9);
        assertEquals(42.0, stats.getMax(), 1e-9);
        assertEquals(42.0, stats.getSum(), 1e-9);
        assertNull(stats.getStdDev());
    }

    @Test
    public void testMultipleValues() {
        ColumnStats stats = new ColumnStats();
        stats.accumulate(1.0);
        stats.accumulate(2.0);
        stats.accumulate(3.0);
        assertEquals(3, stats.getCount());
        assertEquals(2.0, stats.getMean(), 1e-9);
        assertEquals(1.0, stats.getMin(), 1e-9);
        assertEquals(3.0, stats.getMax(), 1e-9);
        assertEquals(6.0, stats.getSum(), 1e-9);
        assertEquals(1.0, stats.getStdDev(), 1e-9);
    }

    @Test
    public void testNullValues() {
        ColumnStats stats = new ColumnStats();
        stats.accumulate(null);
        stats.accumulate(10.0);
        assertEquals(1, stats.getCount());
        assertEquals(1, stats.getNullCount());
        assertEquals(10.0, stats.getMean(), 1e-9);
    }

    @Test
    public void testAllNull() {
        ColumnStats stats = new ColumnStats();
        stats.accumulate(null);
        stats.accumulate(null);
        assertEquals(0, stats.getCount());
        assertEquals(2, stats.getNullCount());
        assertNull(stats.getMean());
        assertNull(stats.getMin());
        assertNull(stats.getMax());
    }
}
