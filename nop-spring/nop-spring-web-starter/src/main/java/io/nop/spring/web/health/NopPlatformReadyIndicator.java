package io.nop.spring.web.health;

import io.nop.core.initialize.CoreInitialization;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 自定义就绪状态检查器 (Readiness Health Indicator).
 * 这个检查器专门用于判断服务是否准备好接收流量。
 */
@Component("nopPlatformReady") // 为这个bean指定一个明确的名称，方便在配置中引用
public class NopPlatformReadyIndicator implements HealthIndicator {

    @Override
    public Health health() {
        // 1. 优先检查是否被挂起 (Suspended)
        if (CoreInitialization.isSuspended()) {
            return Health.down()
                    .withDetail("reason", "Service is suspended")
                    .build();
        }

        // 2. 接着检查是否已初始化 (Initialized)
        if (CoreInitialization.isInitialized()) {
            // 已初始化且未被挂起，服务就绪
            return Health.up().build();
        }

        // 3. 如果既未挂起也未初始化，则说明服务正在启动中
        return Health.down()
                .withDetail("reason", "Service is initializing")
                .build();
    }
}