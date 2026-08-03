/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.api.core.ioc.BeanContainer;

/**
 * Default {@link BeanFunctionResolver} that resolves beans from the NopIoC global
 * {@link BeanContainer}. Suitable for production code; tests typically use
 * {@link #forInstance(Object...)} or a {@link io.nop.ioc.loader.BeanContainerBuilder}
 * backed instance.
 */
public class GlobalBeanFunctionResolver implements BeanFunctionResolver {

    public static final GlobalBeanFunctionResolver INSTANCE = new GlobalBeanFunctionResolver();

    @Override
    public <T> T resolve(String beanName, Class<T> targetType) {
        Object bean = BeanContainer.tryGetBean(beanName);
        if (bean == null) {
            throw new IllegalArgumentException(
                    "Stream DSL bean reference not found in BeanContainer: bean='" + beanName + "'");
        }
        if (!targetType.isInstance(bean)) {
            throw new IllegalArgumentException(
                    "Stream DSL bean '" + beanName + "' is not a " + targetType.getName()
                            + ": actual=" + bean.getClass().getName());
        }
        return targetType.cast(bean);
    }

    @Override
    public boolean contains(String beanName) {
        return BeanContainer.tryGetBean(beanName) != null;
    }
}
