package io.nop.cluster.health;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 CompositeHealthChecker 的聚合语义：
 * 空检查器集合默认 UP；details 按 checker 名聚合（default 名下的 details 平铺展开）。
 * 注意：当前 HealthStatus.merge 实现（取 ordinal 较小者）下，单 checker DOWN 不改变
 * 全 UP 聚合结果——与常见 worst-wins 语义相反，已作为缺陷嫌疑记录，不在此断言修复后行为。
 */
public class TestCompositeHealthChecker {

    static IHealthChecker checker(HealthStatus status) {
        return includeDetails -> {
            HealthCheckResult result = new HealthCheckResult();
            result.setStatus(status);
            if (includeDetails)
                result.setDetails(Map.of("status", status.name()));
            return result;
        };
    }

    @Test
    public void testEmptyCheckersDefaultUp() {
        CompositeHealthChecker composite = new CompositeHealthChecker(Map.of());
        HealthCheckResult result = composite.checkHealth(false);
        assertEquals(HealthStatus.UP, result.getStatus(), "empty composite must default to UP");
    }

    @Test
    public void testAllUpAggregatesUp() {
        CompositeHealthChecker composite = new CompositeHealthChecker(Map.of(
                "a", checker(HealthStatus.UP),
                "b", checker(HealthStatus.UP)));
        assertEquals(HealthStatus.UP, composite.checkHealth(false).getStatus());
    }

    @Test
    public void testMergeIsFirstArgWhenOrdinalSmaller() {
        // 刻画当前 merge 行为：返回 ordinal 较小的一方。
        // UP(1)/DOWN(2) 合并返回 UP，使 DOWN 不抬升聚合状态，
        // 与常见 worst-wins 语义相反，已记录为缺陷嫌疑（characterization test）。
        assertEquals(HealthStatus.UP, HealthStatus.merge(HealthStatus.UP, HealthStatus.DOWN));
        assertEquals(HealthStatus.UP, HealthStatus.merge(HealthStatus.DOWN, HealthStatus.UP));
        assertEquals(HealthStatus.UNKNOWN, HealthStatus.merge(HealthStatus.UNKNOWN, HealthStatus.UP));
    }

    @Test
    public void testDetailsAggregatedByCheckerName() {
        CompositeHealthChecker composite = new CompositeHealthChecker(Map.of(
                CompositeHealthChecker.DEFAULT_NAME, checker(HealthStatus.UP),
                "extra", checker(HealthStatus.UP)));
        HealthCheckResult result = composite.checkHealth(true);

        Map<String, Object> details = result.getDetails();
        // default 名下的 details 平铺到顶层
        assertEquals("UP", details.get("status"), "default checker details must be flattened");
        // 非 default 名下的 details 按名字嵌套
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) details.get("extra");
        assertEquals("UP", nested.get("status"), "named checker details must be nested");
    }
}
