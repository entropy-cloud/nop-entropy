/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.core.ioc;

import io.nop.api.core.ioc.IBeanContainer;
import org.springframework.context.ConfigurableApplicationContext;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.Map;

public class NopSpringBeanContainer implements IBeanContainer {
    private String id = "spring";
    private final ConfigurableApplicationContext context;

    public NopSpringBeanContainer(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void restart() {

    }

    @Override
    public boolean isRunning() {
        return context.isRunning();
    }

    @Override
    public boolean containsBean(String name) {
        return context.containsBean(name);
    }

    @Nonnull
    @Override
    public Object getBean(String name) {
        return context.getBean(name);
    }

    @Override
    public String getBeanScope(String name) {
        return context.getBeanFactory().getBeanDefinition(name).getScope();
    }

    @Nonnull
    @Override
    public <T> T getBeanByType(Class<T> clazz) {
        return context.getBean(clazz);
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        return context.getBeansOfType(clazz);
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annClass) {
        return context.getBeansWithAnnotation(annClass);
    }

    @Override
    public boolean containsBeanType(Class<?> clazz) {
        return context.getBeanNamesForType(clazz).length > 0;
    }

    @Override
    public String findAutowireCandidate(Class<?> beanType) {
        String[] names = context.getBeanNamesForType(beanType);
        if (names.length == 0)
            return null;
        return names[0];
    }
}
