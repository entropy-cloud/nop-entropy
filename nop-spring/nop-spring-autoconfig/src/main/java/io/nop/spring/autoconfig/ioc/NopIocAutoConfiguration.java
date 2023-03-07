/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.autoconfig.ioc;

import io.nop.api.core.ioc.IBeanContainer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NopIocAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(IBeanContainer.class)
    public IBeanContainer nopSpringBeanContainer(ConfigurableApplicationContext context) {
        return new NopSpringBeanContainer(context);
    }
}