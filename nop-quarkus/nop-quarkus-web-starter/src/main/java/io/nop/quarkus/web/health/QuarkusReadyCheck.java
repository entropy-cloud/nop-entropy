/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.web.health;

import io.nop.core.initialize.CoreInitialization;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * 是否已经启动并可以对外提供服务
 */
@Readiness
public class QuarkusReadyCheck implements HealthCheck {
    static final String APP_NAME = "NopPlatform";

    @Override
    public HealthCheckResponse call() {
        // 使用Builder模式创建响应，更灵活
        HealthCheckResponseBuilder responseBuilder = HealthCheckResponse.named(APP_NAME);

        // 1. 优先检查是否被挂起。挂起状态意味着服务不可用。
        if (CoreInitialization.isSuspended()) {
            return responseBuilder.down()
                    .withData("reason", "Service is suspended")
                    .build();
        }

        // 2. 接着检查是否已初始化。
        if (CoreInitialization.isInitialized()) {
            // 已初始化且未被挂起，服务就绪
            return responseBuilder.up().build();
        }

        // 3. 如果既未挂起也未初始化，则说明服务正在启动中。
        return responseBuilder.down()
                .withData("reason", "Service is initializing")
                .build();
    }
}
