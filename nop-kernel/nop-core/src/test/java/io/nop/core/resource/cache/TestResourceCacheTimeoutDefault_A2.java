package io.nop.core.resource.cache;

import io.nop.api.core.config.AppConfig;
import org.junit.jupiter.api.Test;

import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A2（plan 2573，2026-11-14 独立评审）：组件缓存 TTL（漏检自愈通道）缺省值钉。
 *
 * <p>缺省必须为 null（不启用）——plan 2500 裁定③曾把缺省翻转为 5min，独立评审确认该缺省使
 * 全部组件缓存（`ResourceLoadingCache` expireAfterWrite 逐出 + entry 层 maxAge 强制重载双通道）
 * 默认每 5 分钟周期性冷重建（默认性能翻转、升级方无提示），A2 修订回 null：自愈通道按部署
 * 显式启用（正值 opt-in）。缺省回归后本钉防再次静默翻转。
 */
public class TestResourceCacheTimeoutDefault_A2 {

    @Test
    void resourceCacheTimeoutDefaultIsNull_optInOnly_A2() {
        // 先清 override（防同 JVM 前序测试残留赋值），断言生效值即缺省
        AppConfig.getConfigProvider().updateConfigValue(CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT, null);
        assertNull(CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT.get(),
                "A2: nop.core.component.resource-cache.timeout must default to null "
                        + "(self-healing TTL channel is opt-in; the 5min default was reverted by plan 2573)");
    }
}
