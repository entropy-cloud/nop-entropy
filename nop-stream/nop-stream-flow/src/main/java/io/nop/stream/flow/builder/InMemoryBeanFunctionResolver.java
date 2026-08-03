/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link BeanFunctionResolver} backed by an explicit in-memory registry. Tests use this
 * to register {@code SourceFunction}/{@code SinkFunction} stubs without spinning up a full
 * NopIoC container.
 *
 * <p>Tests that want to verify the production bean-container integration should use
 * {@link GlobalBeanFunctionResolver} together with a {@link io.nop.ioc.loader.BeanContainerBuilder}.
 */
public final class InMemoryBeanFunctionResolver implements BeanFunctionResolver {

    private final Map<String, Object> beans = new LinkedHashMap<>();

    public InMemoryBeanFunctionResolver register(String name, Object bean) {
        beans.put(name, bean);
        return this;
    }

    @Override
    public <T> T resolve(String beanName, Class<T> targetType) {
        Object bean = beans.get(beanName);
        if (bean == null) {
            throw new IllegalArgumentException(
                    "Stream DSL bean reference not registered: bean='" + beanName + "'");
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
        return beans.containsKey(beanName);
    }
}
