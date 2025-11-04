/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.ioc;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.exceptions.NopException;

import static io.nop.api.core.ApiErrors.ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED;

@GlobalInstance
public class BeanContainer {
    static IBeanContainerProvider _provider;

    public static boolean isInitialized() {
        return _provider != null;
    }

    public static IBeanContainer instance() {
        IBeanContainerProvider provider = _provider;
        if (provider == null)
            throw new NopException(ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED);
        return provider.getBeanContainer();
    }

    public static void registerInstance(IBeanContainer container) {
        _provider = () -> container;
    }

    public static void registerProvider(IBeanContainerProvider provider) {
        _provider = provider;
    }

    public static <T> T getBeanByType(Class<T> beanClass) {
        return instance().getBeanByType(beanClass);
    }

    public static Object tryGetBean(String beanName) {
        if (_provider == null)
            return null;
        IBeanContainer container = instance();
        if (!container.containsBean(beanName))
            return null;
        return container.getBean(beanName);
    }
}
