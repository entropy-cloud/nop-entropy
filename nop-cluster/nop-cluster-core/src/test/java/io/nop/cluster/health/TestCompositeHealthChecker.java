package io.nop.cluster.health;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 CompositeHealthChecker 的聚合语义：
 * 空检查器集合默认 UP；details 按 checker 名聚合（default 名下的 details 平铺展开）；
 * worst-wins 聚合：任一 checker DOWN 时聚合状态必须降为 DOWN（回归覆盖 wi11#1）。
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
    public void testSingleDownCheckerPullsAggregateDown() {
        CompositeHealthChecker composite = new CompositeHealthChecker(Map.of(
                "a", checker(HealthStatus.UP),
                "b", checker(HealthStatus.DOWN)));
        assertEquals(HealthStatus.DOWN, composite.checkHealth(false).getStatus(),
                "worst-wins: one DOWN checker must degrade the aggregate");
    }

    @Test
    public void testMergeIsWorstWinsWithSpringBootSeverityOrder() {
        // 回归覆盖 wi11#1：merge 返回更严重一方，严重度对齐 Spring Boot 默认次序
        // DOWN > OUT_OF_SERVICE > UP > UNKNOWN
        assertEquals(HealthStatus.DOWN, HealthStatus.merge(HealthStatus.UP, HealthStatus.DOWN));
        assertEquals(HealthStatus.DOWN, HealthStatus.merge(HealthStatus.DOWN, HealthStatus.UP));
        assertEquals(HealthStatus.UP, HealthStatus.merge(HealthStatus.UNKNOWN, HealthStatus.UP));
        assertEquals(HealthStatus.OUT_OF_SERVICE, HealthStatus.merge(HealthStatus.UP, HealthStatus.OUT_OF_SERVICE));
        assertEquals(HealthStatus.DOWN, HealthStatus.merge(HealthStatus.DOWN, HealthStatus.OUT_OF_SERVICE));
        assertEquals(HealthStatus.OUT_OF_SERVICE, HealthStatus.merge(HealthStatus.OUT_OF_SERVICE, HealthStatus.UNKNOWN));
        // 同级保持先到者
        assertEquals(HealthStatus.UP, HealthStatus.merge(HealthStatus.UP, HealthStatus.UP));
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
