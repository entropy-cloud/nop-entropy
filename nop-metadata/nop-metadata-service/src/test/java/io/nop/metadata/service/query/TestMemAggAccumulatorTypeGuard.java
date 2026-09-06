package io.nop.metadata.service.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * check2 P3-12（2026-08-23 审计）回归（子项 1）：MinAcc/MaxAcc 对非 Comparable 值裸 ClassCastException。
 *
 * <p>缺陷机制：跨库内存聚合 min/max 直接 {@code (Comparable<Object>) v} 强转——JDBC 返回的
 * byte[]/Struct 等非 Comparable 类型抛未包装 CCE，错误不可诊断。
 *
 * <p>修复：accumulate 加 {@code instanceof Comparable} 守卫，非 Comparable 值按 null 语义跳过
 * （result 为 null，不伪造）。
 *
 * <p>mutate-fail：回退为无守卫强转时 accumulate(new byte[0]) 抛裸 ClassCastException → 测试失败。
 */
public class TestMemAggAccumulatorTypeGuard {

    private static final byte[] NON_COMPARABLE = new byte[]{1, 2, 3};

    /** MinAcc：非 Comparable 值不抛 CCE，结果 null；Comparable 值正常聚合。 */
    @Test
    public void testMinAccSkipsNonComparableValues() {
        AggregationContext.MinAcc acc = new AggregationContext.MinAcc();
        acc.accumulate(NON_COMPARABLE);
        assertNull(acc.result(), "non-Comparable value must be skipped, result stays null (no CCE)");

        acc.accumulate(3);
        acc.accumulate(NON_COMPARABLE);
        acc.accumulate(1);
        assertEquals(1, acc.result(), "Comparable values must aggregate normally around skipped ones");
    }

    /** MaxAcc：非 Comparable 值不抛 CCE，结果 null；Comparable 值正常聚合。 */
    @Test
    public void testMaxAccSkipsNonComparableValues() {
        AggregationContext.MaxAcc acc = new AggregationContext.MaxAcc();
        acc.accumulate(NON_COMPARABLE);
        assertNull(acc.result(), "non-Comparable value must be skipped, result stays null (no CCE)");

        acc.accumulate(3);
        acc.accumulate(NON_COMPARABLE);
        acc.accumulate(7);
        assertEquals(7, acc.result(), "Comparable values must aggregate normally around skipped ones");
    }

    /** null 值语义不变（跳过）。 */
    @Test
    public void testNullValuesStillSkipped() {
        AggregationContext.MinAcc min = new AggregationContext.MinAcc();
        min.accumulate(null);
        assertNull(min.result());
        AggregationContext.MaxAcc max = new AggregationContext.MaxAcc();
        max.accumulate(null);
        assertNull(max.result());
        // 无任何累计值的既有语义（result null）不受影响
        assertNull(new AggregationContext.MinAcc().result());
        assertNull(new AggregationContext.MaxAcc().result());
    }
}
