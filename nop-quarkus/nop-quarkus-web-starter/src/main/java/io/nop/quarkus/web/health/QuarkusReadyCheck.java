/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.quarkus.web.health;

import io.nop.core.initialize.CoreInitialization;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * 是否已经启动并可以对外提供服务
 */
@Readiness
public class QuarkusReadyCheck implements HealthCheck {
    static final String APP_NAME = "NopPlatform";

    @Override
    public HealthCheckResponse call() {
        boolean inited = CoreInitialization.isInitialized();
        return inited ? HealthCheckResponse.up(APP_NAME) : HealthCheckResponse.down(APP_NAME);
    }
}
