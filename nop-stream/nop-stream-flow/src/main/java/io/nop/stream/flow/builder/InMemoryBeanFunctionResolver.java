/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import java.util.Collections;
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

    /**
     * Item 14 (distributed): read-only view of the registered bindings, so a
     * launch-side factory can snapshot the bean set into a serializable pipeline
     * spec ({@code RemotePipelineSpec}) for cross-JVM deployment.
     */
    public Map<String, Object> getBeans() {
        return Collections.unmodifiableMap(beans);
    }

    @Override
    public <T> T resolve(String beanName, Class<T> targetType) {
        Object bean = beans.get(beanName);
        if (bean == null) {
            throw StreamModelDslBuilder.beanNotFound(beanName);
        }
        if (!targetType.isInstance(bean)) {
            throw StreamModelDslBuilder.beanTypeMismatch(beanName, targetType, bean.getClass());
        }
        return targetType.cast(bean);
    }

    @Override
    public boolean contains(String beanName) {
        return beans.containsKey(beanName);
    }
}
