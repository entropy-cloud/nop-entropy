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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

@ComponentScan("io.nop.spring")
public class NopSpringCoreAutoConfig {

    @Autowired
    ApplicationArguments globalArgs;

    @EventListener
    public void onStart(ApplicationStartedEvent event) {
        //AppConfig.getConfigProvider().updateConfigValue(CFG_DEBUG, true);
        NopSpringBeanContainer container = new NopSpringBeanContainer(event.getApplicationContext());
        BeanContainer.registerInstance(container);

        new NopApplication().run(globalArgs.getSourceArgs());
    }

    @EventListener
    public void onClose(ContextClosedEvent event) {
        CoreInitialization.destroy();
    }
}
