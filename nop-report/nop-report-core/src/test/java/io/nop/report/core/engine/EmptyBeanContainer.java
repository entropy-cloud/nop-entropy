/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.api.core.ioc.IBeanContainer;
import jakarta.annotation.Nonnull;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * 测试用空容器：注销 SPI fake bean 时恢复「未注册」语义（containsBean 恒 false）。
 */
public class EmptyBeanContainer implements IBeanContainer {
    @Override
    public String getId() {
        return "empty";
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
        return true;
    }

    @Override
    public boolean containsBean(String name) {
        return false;
    }

    @Nonnull
    @Override
    public Object getBean(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsBeanType(Class<?> clazz) {
        return false;
    }

    @Nonnull
    @Override
    public <T> T getBeanByType(Class<T> clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T tryGetBeanByType(Class<T> clazz) {
        return null;
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        return Map.of();
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annClass) {
        return Map.of();
    }

    @Override
    public String getBeanScope(String name) {
        return "singleton";
    }

    @Override
    public Class<?> getBeanClass(String name) {
        return null;
    }

    @Override
    public String findAutowireCandidate(Class<?> clazz) {
        return null;
    }
}
