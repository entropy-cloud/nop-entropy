package io.nop.job.api.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestResourceVector {

    @Test
    void testZeroConstant() {
        assertEquals(0, ResourceVector.ZERO.getCpu());
        assertEquals(0, ResourceVector.ZERO.getMemory());
    }

    /**
     * AR-97：add 溢出时显式抛 ArithmeticException（Math.addExact），而非静默回绕成负数。
     */
    @Test
    void testAddOverflowThrows() {
        ResourceVector nearMax = new ResourceVector(Integer.MAX_VALUE, 0);
        ResourceVector one = new ResourceVector(1, 0);
        assertThrows(ArithmeticException.class, () -> nearMax.add(one),
                "cpu overflow must throw ArithmeticException, not silently wrap to negative");
        ResourceVector nearMaxMem = new ResourceVector(0, Integer.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> nearMaxMem.add(new ResourceVector(0, 1)),
                "memory overflow must throw ArithmeticException");
    }

    @Test
    void testMaxValueConstant() {
        assertEquals(Integer.MAX_VALUE, ResourceVector.MAX_VALUE.getCpu());
        assertEquals(Integer.MAX_VALUE, ResourceVector.MAX_VALUE.getMemory());
    }

    @Test
    void testAdd() {
        ResourceVector a = new ResourceVector(500, 1024);
        ResourceVector b = new ResourceVector(300, 2048);
        ResourceVector sum = a.add(b);
        assertEquals(800, sum.getCpu());
        assertEquals(3072, sum.getMemory());
        // 不可变：原实例不变
        assertEquals(500, a.getCpu());
        assertEquals(1024, a.getMemory());
    }

    @Test
    void testSubtractAllowsNegative() {
        ResourceVector capacity = new ResourceVector(1000, 2048);
        ResourceVector reserved = new ResourceVector(1500, 1024);
        ResourceVector remaining = capacity.subtract(reserved);
        // 允许负值，不 clamp
        assertEquals(-500, remaining.getCpu());
        assertEquals(1024, remaining.getMemory());
    }

    @Test
    void testSubtractZero() {
        ResourceVector v = new ResourceVector(1000, 2048);
        // 减 ZERO 不影响（向后兼容未声明 cost 的 task）
        ResourceVector r = v.subtract(ResourceVector.ZERO);
        assertEquals(1000, r.getCpu());
        assertEquals(2048, r.getMemory());
    }

    @Test
    void testFitsAllDimensionsSufficient() {
        ResourceVector remaining = new ResourceVector(2000, 4096);
        ResourceVector cost = new ResourceVector(500, 1024);
        assertTrue(remaining.fits(cost));
    }

    @Test
    void testFitsCpuInsufficient() {
        ResourceVector remaining = new ResourceVector(400, 4096);
        ResourceVector cost = new ResourceVector(500, 1024);
        // CPU 维不足 → false（即使 memory 维充足）
        assertFalse(remaining.fits(cost));
    }

    @Test
    void testFitsMemoryInsufficient() {
        ResourceVector remaining = new ResourceVector(2000, 512);
        ResourceVector cost = new ResourceVector(500, 1024);
        // memory 维不足 → false
        assertFalse(remaining.fits(cost));
    }

    @Test
    void testFitsZeroCostAlwaysFits() {
        // cost=0 的任务 always fit（向后兼容）
        ResourceVector remaining = new ResourceVector(0, 0);
        assertTrue(remaining.fits(ResourceVector.ZERO));
    }

    @Test
    void testFitsExact() {
        ResourceVector remaining = new ResourceVector(500, 1024);
        ResourceVector cost = new ResourceVector(500, 1024);
        // 相等 → fits（>=）
        assertTrue(remaining.fits(cost));
    }

    @Test
    void testIsZeroOrNegativeAnyDim() {
        // 任一维 <= 0 即 true
        assertTrue(new ResourceVector(-500, 1024).isZeroOrNegative());
        assertTrue(new ResourceVector(500, -1).isZeroOrNegative());
        assertTrue(new ResourceVector(0, 1024).isZeroOrNegative());
        assertTrue(new ResourceVector(500, 0).isZeroOrNegative());
        assertTrue(ResourceVector.ZERO.isZeroOrNegative());
    }

    @Test
    void testIsZeroOrNegativeAllPositive() {
        // 两维都 > 0 → false
        assertFalse(new ResourceVector(1, 1).isZeroOrNegative());
        assertFalse(new ResourceVector(500, 1024).isZeroOrNegative());
    }

    @Test
    void testLoadScoreBalanced() {
        ResourceVector reserved = new ResourceVector(500, 1024);
        ResourceVector capacity = new ResourceVector(2000, 4096);
        // cpu 比例 = 0.25, memory 比例 = 0.25 → max = 0.25
        assertEquals(0.25, reserved.loadScore(capacity), 0.0001);
    }

    @Test
    void testLoadScoreCpuDominant() {
        ResourceVector reserved = new ResourceVector(1800, 1024);
        ResourceVector capacity = new ResourceVector(2000, 4096);
        // cpu = 0.9, memory = 0.25 → max = 0.9
        assertEquals(0.9, reserved.loadScore(capacity), 0.0001);
    }

    @Test
    void testLoadScoreMemoryDominant() {
        ResourceVector reserved = new ResourceVector(100, 3500);
        ResourceVector capacity = new ResourceVector(2000, 4096);
        // cpu = 0.05, memory ≈ 0.854 → max ≈ 0.854
        assertEquals(0.854, reserved.loadScore(capacity), 0.001);
    }

    @Test
    void testLoadScoreZeroReserved() {
        // reserved=0 → loadScore=0（最闲）
        assertEquals(0.0, ResourceVector.ZERO.loadScore(new ResourceVector(2000, 4096)), 0.0001);
    }

    @Test
    void testLoadScoreMaxValueCapacity() {
        ResourceVector reserved = new ResourceVector(500, 1024);
        // capacity=MAX_VALUE（未声明）→ loadScore=0（退化为最闲）
        assertEquals(0.0, reserved.loadScore(ResourceVector.MAX_VALUE), 0.0001);
    }

    @Test
    void testLoadScorePartialMaxValueCapacity() {
        // 单维 MAX_VALUE：该维按 0 计
        ResourceVector reserved = new ResourceVector(500, 2048);
        ResourceVector capacity = new ResourceVector(Integer.MAX_VALUE, 4096);
        // cpu 维 MAX_VALUE → 0；memory = 2048/4096 = 0.5 → max = 0.5
        assertEquals(0.5, reserved.loadScore(capacity), 0.0001);
    }

    @Test
    void testMaxValueSubtractReservedStaysLarge() {
        // MAX_VALUE capacity 减去有限 reserved 仍很大，isZeroOrNegative 为 false
        ResourceVector remaining = ResourceVector.MAX_VALUE.subtract(new ResourceVector(100000, 100000));
        assertFalse(remaining.isZeroOrNegative());
        // 仍能 fit 任何有限 cost
        assertTrue(remaining.fits(new ResourceVector(99999, 99999)));
    }
}
