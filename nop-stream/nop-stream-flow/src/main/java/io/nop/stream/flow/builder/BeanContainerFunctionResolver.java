/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.api.core.ioc.IBeanContainer;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * D2 bean-source form 2 (pre-submit-validation-design.md §3): adapts an explicitly
 * assembled NopIoC {@link IBeanContainer} to the {@link BeanFunctionResolver} contract,
 * without entering the global container. Hosts that keep an independently assembled
 * container (e.g. the item 19 connector-registry assembly) hand it to conf-validate /
 * dry-run wrapped in this resolver.
 *
 * <p>{@code IBeanContainer} lives in nop-api-core, so this adapter adds no dependency
 * beyond what flow already carries through nop-stream-core.
 */
public final class BeanContainerFunctionResolver implements BeanFunctionResolver {

    private final IBeanContainer container;

    public BeanContainerFunctionResolver(IBeanContainer container) {
        if (container == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "container");
        }
        this.container = container;
    }

    public static BeanContainerFunctionResolver of(IBeanContainer container) {
        return new BeanContainerFunctionResolver(container);
    }

    @Override
    public <T> T resolve(String beanName, Class<T> targetType) {
        Object bean = tryGetBean(beanName);
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
        return tryGetBean(beanName) != null;
    }

    private Object tryGetBean(String beanName) {
        return container.containsBean(beanName) ? container.getBean(beanName) : null;
    }
}
