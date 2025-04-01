package io.nop.ioc.impl;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import jakarta.annotation.Nonnull;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;

import static io.nop.ioc.IocErrors.ARG_BEANS;
import static io.nop.ioc.IocErrors.ARG_BEAN_NAME;
import static io.nop.ioc.IocErrors.ARG_BEAN_TYPE;
import static io.nop.ioc.IocErrors.ARG_CONTAINER_ID;
import static io.nop.ioc.IocErrors.ERR_IOC_MULTIPLE_BEAN_WITH_TYPE;

public class DelegateBeanContainer implements IBeanContainer {
    private final String id;
    private final IBeanContainer container;
    private final Set<String> allowedNames;

    public DelegateBeanContainer(String id, IBeanContainer container, Set<String> allowedNames) {
        this.id = id;
        this.container = container;
        this.allowedNames = allowedNames;
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
        if (!allowedNames.contains(name))
            return false;
        return container.containsBean(name);
    }

    @Override
    public boolean isRunning() {
        return container.isRunning();
    }

    @Nonnull
    @Override
    public Object getBean(String name) {
        if (allowedNames.contains(name))
            return container.getBean(name);
        throw new NopException(ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_NAME).param(ARG_BEAN_NAME, name);
    }

    @Override
    public boolean containsBeanType(Class<?> clazz) {
        return !getBeansOfType(clazz).isEmpty();
    }

    @Nonnull
    @Override
    public <T> T getBeanByType(Class<T> clazz) {
        T ret = tryGetBeanByType(clazz);
        if (ret == null)
            throw new NopException(ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_TYPE).param(ARG_BEAN_TYPE, clazz)
                    .param(ARG_CONTAINER_ID, getId());
        return ret;
    }

    @Override
    public <T> T tryGetBeanByType(Class<T> clazz) {
        Map<String, T> beans = getBeansOfType(clazz);
        if (beans.isEmpty())
            return null;
        if (beans.size() == 1)
            return beans.values().iterator().next();

        throw new NopException(ERR_IOC_MULTIPLE_BEAN_WITH_TYPE).param(ARG_BEAN_TYPE, clazz)
                .param(ARG_BEANS, beans.keySet());
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        Map<String, T> beans = container.getBeansOfType(clazz);
        beans.keySet().retainAll(allowedNames);
        return beans;
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annClass) {
        Map<String, Object> beans = container.getBeansWithAnnotation(annClass);
        beans.keySet().retainAll(allowedNames);
        return beans;
    }

    @Override
    public String getBeanScope(String name) {
        if (!allowedNames.contains(name))
            throw new NopException(ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_NAME).param(ARG_BEAN_NAME, name);
        return container.getBeanScope(name);
    }

    @Override
    public Class<?> getBeanClass(String name) {
        if (!allowedNames.contains(name))
            throw new NopException(ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_NAME).param(ARG_BEAN_NAME, name);
        return container.getBeanClass(name);
    }

    @Override
    public String findAutowireCandidate(Class<?> beanType) {
        Map<String, ?> beans = this.getBeansOfType(beanType);
        if (beans.isEmpty())
            return null;
        if (beans.size() == 1)
            return beans.keySet().iterator().next();

        throw new NopException(ERR_IOC_MULTIPLE_BEAN_WITH_TYPE).param(ARG_BEAN_TYPE, beanType)
                .param(ARG_BEANS, beans.keySet());
    }
}
