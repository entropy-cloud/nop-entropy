package io.nop.quarkus.core.dao;

import io.agroal.api.AgroalDataSourceMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 4.4-C6-005（plan 2562 裁定 I）载体：exportMetrics 判存移除旧绑定后重注册——
 * 热重载（同一 datasource 名两次 init）不再叠绑，Gauge 数量稳定为 17。
 *
 * <p>Micrometer 对同名同 tag 的 Gauge 重复注册会抛 IllegalArgumentException，
 * 修复前第二次 exportMetrics 直接失败——本载体即其红面。</p>
 */
public class TestAgroalMetricsRebind {

    private static final String DATASOURCE = "test-ds";

    /** Metrics interface stub via dynamic proxy: pool is never touched, defaults suffice. */
    private static AgroalDataSourceMetrics proxyMetrics(int activeCount) {
        return (AgroalDataSourceMetrics) Proxy.newProxyInstance(
                TestAgroalMetricsRebind.class.getClassLoader(),
                new Class<?>[]{AgroalDataSourceMetrics.class},
                (proxy, method, args) -> {
                    Class<?> rt = method.getReturnType();
                    if (rt == Duration.class)
                        return Duration.ZERO;
                    if (rt == boolean.class)
                        return Boolean.FALSE;
                    if (method.getName().equals("activeCount"))
                        return (long) activeCount;
                    if (rt == int.class)
                        return 0;
                    if (rt == long.class)
                        return 0L;
                    return null;
                });
    }

    private long agroalGaugeCount(SimpleMeterRegistry registry) {
        return registry.getMeters().stream()
                .filter(m -> m.getId().getName().startsWith("agroal."))
                .filter(m -> DATASOURCE.equals(m.getId().getTag("datasource")))
                .count();
    }

    @Test
    public void testRebindKeepsGaugeCountStable() {
        AgroalDataSourceFactory factory = new AgroalDataSourceFactory();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        factory.exportMetrics(registry, DATASOURCE, proxyMetrics(1));
        assertEquals(17, agroalGaugeCount(registry),
                "first init binds exactly the 17 agroal gauges");

        // 热重载形态：同 datasource 名第二次 init（新池、新 metrics 句柄）
        factory.exportMetrics(registry, DATASOURCE, proxyMetrics(42));
        assertEquals(17, agroalGaugeCount(registry),
                "rebind must remove the old binding first: gauge count must not stack");

        // 判存 remove 的语义面：Gauge 必须绑定新池的 metrics 句柄——Micrometer 对
        // 同名同 tag 的 Gauge 重复注册是幂等返回旧实例（静默陈旧绑定），不抛异常，
        // 故红面断言的是"读到新值"而非"数量不叠"。
        Gauge active = registry.find("agroal.active.count").tag("datasource", DATASOURCE).gauge();
        assertNotNull(active);
        assertEquals(42.0, active.value(),
                "rebind must swap the gauge onto the new datasource metrics handle");
    }
}
