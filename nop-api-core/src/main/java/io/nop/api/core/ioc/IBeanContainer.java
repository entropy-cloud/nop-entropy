/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.ioc;

import jakarta.annotation.Nonnull;

import java.lang.annotation.Annotation;
import java.util.Map;

public interface IBeanContainer extends IBeanProvider {
    String getId();

    void start();

    /**
     * 异步启动时强制等待启动完毕
     */
    default void awaitStartFinished(){

    }

    void stop();

    void restart();

    boolean containsBean(String name);

    boolean isRunning();

    @Nonnull
    Object getBean(String name);

    boolean containsBeanType(Class<?> clazz);

    @Nonnull
    <T> T getBeanByType(Class<T> clazz);

    <T> T tryGetBeanByType(Class<T> clazz);

    <T> Map<String, T> getBeansOfType(Class<T> clazz);

    Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annClass);

    String getBeanScope(String name);

    Class<?> getBeanClass(String name);

    String findAutowireCandidate(Class<?> beanType);

    default boolean supportInjectTo() {
        return false;
    }

    default void injectTo(Object bean) {
        throw new UnsupportedOperationException("not support autowire");
    }
}