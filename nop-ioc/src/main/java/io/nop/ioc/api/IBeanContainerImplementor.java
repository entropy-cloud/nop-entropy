/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.api;

import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.ioc.BeanContainerStartMode;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.commons.lang.IClassLoader;
import io.nop.commons.service.ILifeCycle;
import io.nop.core.lang.xml.XNode;
import io.nop.ioc.impl.IBeanClassIntrospection;

import jakarta.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.Map;

public interface IBeanContainerImplementor extends IBeanContainer, ILifeCycle {
    boolean isStarted();

    void restart();

    BeanContainerStartMode getStartMode();

    Object getConfigValue(String varName);

    IConfigProvider getConfigProvider();

    void refreshConfig(String beanId);

    void destroyBean(String beanName, Object bean);

    IBeanClassIntrospection getClassIntrospection();

    IClassLoader getClassLoader();

    Object getBean(@Nonnull String name, boolean includeCreating);

    <T> T getBeanByType(Class<T> requiredType, boolean includeCreating);

    String findAutowireCandidate(Class<?> beanType);

    /**
     * 返回当前所有激活的bean对应的配置
     */
    XNode toConfigNode();

    IBeanDefinition getBeanDefinition(String name);

    Map<String, IBeanDefinition> getBeanDefinitionsByType(Class<?> requiredType);

    Map<String, IBeanDefinition> getBeanDefinitionsByAnnotation(Class<? extends Annotation> annClass);
}