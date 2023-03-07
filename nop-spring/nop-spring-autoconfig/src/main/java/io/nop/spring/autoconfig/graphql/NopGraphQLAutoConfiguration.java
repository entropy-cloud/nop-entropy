/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.autoconfig.graphql;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.impl.BizObjectManager;
import io.nop.graphql.core.engine.GraphQLEngine;
import io.nop.graphql.core.engine.IGraphQLEngine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(BizObjectManager.class)
public class NopGraphQLAutoConfiguration {

    @Bean("bizModelBeans")
    @ConditionalOnMissingBean(name = "bizModelBeans")
    public Collection<Object> bizModelBeans(ApplicationContext context) {
        return context.getBeansWithAnnotation(BizModel.class).values();
    }

    @Bean
    @ConditionalOnMissingBean
    public BizObjectManager bizObjectManager(@Qualifier("bizModelBeans") Collection<Object> beans) {
        return new BizObjectManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public IGraphQLEngine graphQLEngine() {
        return new GraphQLEngine();
    }
}
