/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.demo.spring;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.boot.NopApplication;
import io.nop.spring.core.ioc.NopSpringBeanContainer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;

import static io.nop.api.core.ApiConfigs.CFG_DEBUG;

@SpringBootApplication(scanBasePackages = {"io.nop.spring","io.nop.demo.spring"})
public class SpringDemoMain {

    private static String[] globalArgs;

    @EventListener
    public void onStart(ApplicationStartedEvent event) {
        AppConfig.getConfigProvider().updateConfigValue(CFG_DEBUG, true);
        NopSpringBeanContainer container = new NopSpringBeanContainer(event.getApplicationContext());
        BeanContainer.registerInstance(container);

        new NopApplication().run(globalArgs);
    }


    public static void main(String[] args) {
        globalArgs = args;
        SpringApplication.run(SpringDemoMain.class, args);
    }
}
