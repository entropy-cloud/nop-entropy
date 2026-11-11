/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.core;

import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * 4.4-MA-04（plan 2562 裁定 B②）：CDI ShutdownEvent 钩子，一处覆盖全部 Quarkus
 * app main——调用 {@link QuarkusIntegration#stop()} 撤销 start() 的集成层注册面。
 * 各 app main 已有的 ShutdownEvent 处理（CoreInitialization.destroy）保持不变，
 * 本 bean 不重复 destroy 平台生命周期。模块经 jandex-maven-plugin 索引，CDI 可发现。
 */
@ApplicationScoped
public class QuarkusIntegrationShutdownObserver {

    public void onShutdown(@Observes ShutdownEvent event) {
        QuarkusIntegration.stop();
    }
}
