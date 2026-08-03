/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

/**
 * Resolves {@code bean="xxx"} function references inside a parsed
 * {@code io.nop.stream.flow.model.StreamModel} to live function instances
 * ({@code SourceFunction}/{@code SinkFunction}/...).
 *
 * <p>The default implementation looks up beans from the NopIoC global
 * {@link io.nop.api.core.ioc.BeanContainer}. Tests can install a custom resolver
 * backed by an in-process {@link io.nop.api.core.ioc.IBeanContainer} built from
 * a {@code *.beans.xml} via {@link io.nop.ioc.loader.BeanContainerBuilder}.
 */
public interface BeanFunctionResolver {

    /**
     * Look up the bean by name and adapt it to the requested function type.
     *
     * @param beanName   the {@code bean="..."} attribute value
     * @param targetType the expected function interface
     * @param <T>        the function type
     * @return the resolved function instance (never {@code null})
     * @throws IllegalArgumentException if the bean is missing or not assignable
     */
    <T> T resolve(String beanName, Class<T> targetType);

    /**
     * Returns {@code true} if a bean with the given name is available. Used by the
     * builder to detect missing bean declarations and fail fast.
     */
    boolean contains(String beanName);
}
