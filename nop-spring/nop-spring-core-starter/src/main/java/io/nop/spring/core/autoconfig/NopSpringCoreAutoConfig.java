/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.core.autoconfig;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.boot.NopApplication;
import io.nop.core.initialize.CoreInitialization;
import io.nop.spring.core.ioc.NopSpringBeanContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

@ComponentScan("io.nop.spring")
public class NopSpringCoreAutoConfig {

    @Autowired
    ApplicationArguments globalArgs;

    /**
     * 4.4-MA-04（plan 2562 裁定 B①）：本上下文注册的桥接容器。归属判定的锚点：
     * onStart 先注册桥接容器，随后的 NopApplication.run 驱动 CoreInitialization 初始化时，
     * IocCoreInitializer.initialize() 会用 appContainer 覆盖注册表指针（回写桥，
     * 见 IocCoreInitializer.destroy 的 parentContainer 回写）；因此关闭时注册表指针
     * 仍指向本桥接容器 == 生命周期从未被本上下文推进（宿主已预初始化或已被他人销毁）。
     */
    private NopSpringBeanContainer bridgeContainer;

    @EventListener
    public void onStart(ApplicationStartedEvent event) {
        registerContainer(event.getApplicationContext());

        new NopApplication().run(globalArgs.getSourceArgs());
    }

    /**
     * Package-private seam so lifecycle ownership can be exercised without
     * booting the whole platform in tests. Behavior identical to the previous
     * inline registration.
     */
    void registerContainer(ConfigurableApplicationContext context) {
        bridgeContainer = new NopSpringBeanContainer(context);
        BeanContainer.registerInstance(bridgeContainer);
    }

    @EventListener
    public void onClose(ContextClosedEvent event) {
        // 4.4-MA-04（plan 2562 裁定 B①）：归属守卫——仅本上下文拥有 CoreInitialization
        // 生命周期时才 destroy。注册表指针仍为本上下文的桥接容器，说明 IocCoreInitializer
        // 从未覆盖它（宿主已预初始化的嵌套 Spring 场景），destroy 会把别人拥有的
        // 平台生命周期整体拆掉，故跳过。生命周期被推进过的 owner 场景指针为
        // IocCoreInitializer 的 appContainer（!= bridgeContainer），正常走 destroy。
        if (bridgeContainer == null || BeanContainer.instance() == bridgeContainer) {
            return;
        }
        CoreInitialization.destroy();
    }
}
