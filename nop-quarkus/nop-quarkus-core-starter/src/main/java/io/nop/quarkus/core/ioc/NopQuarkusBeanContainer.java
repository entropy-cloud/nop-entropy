/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.quarkus.core.ioc;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.commons.util.StringHelper;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.impl.ArcContainerImpl;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;


import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

public class NopQuarkusBeanContainer implements IBeanContainer {

    @Override
    public String getId() {
        return "quarkus";
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

    ArcContainer container() {
        return ArcContainerImpl.instance();
    }

    @Override
    public boolean containsBeanType(Class<?> clazz) {
        return container().instance(clazz).isAvailable();
    }

    @Override
    public String findAutowireCandidate(Class<?> beanType) {
        InstanceHandle<?> bean = container().instance(beanType);
        return bean.isAvailable() ? bean.getBean().getName() : null;
    }

    @Override
    public boolean isRunning() {
        return container().isRunning();
    }

    @Override
    public boolean containsBean(String name) {
        return container().bean(name) != null;
    }

    @Override
    public Object getBean(String name) {
        Object bean = container().instance(name).get();
        if (bean == null)
            throw new NopException(ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_NAME).param(ApiErrors.ARG_BEAN_NAME, name);
        return bean;
    }

    @Override
    public <T> T getBeanByType(Class<T> clazz) {
        T bean = container().instance(clazz).get();
        if (bean == null)
            throw new NopException(ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_TYPE).param(ApiErrors.ARG_BEAN_TYPE, clazz);
        return bean;
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        Map<String, T> ret = new HashMap<>();

        for (InstanceHandle<T> handle : Arc.container().select(clazz).handles()) {
            String name = handle.getBean().getName();
            ret.put(name, handle.get());
        }
        return ret;
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annClass) {
        Map<String, Object> ret = new HashMap<>();

        InjectableInstance<Object> handles = container().select(Object.class, new MarkerInterfaceAnnotation(annClass));

        for (InstanceHandle<Object> handle : handles.handles()) {
            String name = handle.getBean().getName();
            ret.put(name, handle.get());
        }
        return ret;
    }

    @Override
    public String getBeanScope(String name) {
        InjectableBean<?> bean = Arc.container().bean(name);
        if (bean == null)
            throw new NopException(ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_NAME).param(ApiErrors.ARG_BEAN_NAME, name);

        Class<? extends Annotation> scope = bean.getScope();
        if (scope == Dependent.class || scope == ApplicationScoped.class)
            return ApiConstants.BEAN_SCOPE_SINGLETON;

        if (scope == RequestScoped.class)
            return ApiConstants.BEAN_SCOPE_REQUEST;

        return StringHelper.camelCaseToHyphen(scope.getSimpleName());
    }
}