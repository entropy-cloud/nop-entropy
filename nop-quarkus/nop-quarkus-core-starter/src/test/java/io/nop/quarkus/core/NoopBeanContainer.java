package io.nop.quarkus.core;

import io.nop.api.core.ioc.IBeanContainer;
import jakarta.annotation.Nonnull;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Map;

/** Minimal container stub for registry pointer pairing assertions. */
public class NoopBeanContainer implements IBeanContainer {
    private final String id;

    public NoopBeanContainer(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
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
    public boolean containsBean(String name) {
        return false;
    }

    @Override
    public boolean isRunning() {
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
        return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annClass) {
        return Collections.emptyMap();
    }

    @Override
    public String getBeanScope(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<?> getBeanClass(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String findAutowireCandidate(Class<?> beanType) {
        return null;
    }
}
