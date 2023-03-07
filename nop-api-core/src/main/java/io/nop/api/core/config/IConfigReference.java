/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.config;

import io.nop.api.core.util.ISourceLocationGetter;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 每个配置项对应一个引用对象。
 *
 * @param <T>
 */
public interface IConfigReference<T> extends ISourceLocationGetter, IConfigValue<T> {
    String getName();

    Class<T> getValueType();

    /**
     * 获取配置项的缺省值
     */
    T getDefaultValue();

    /**
     * 获取配置的值
     */
    T getAssignedValue();

    IConfigValue<T> getProvider();

    /**
     * 是否每次获取时根据上下文环境计算得到动态值
     */
    boolean isDynamic();

    /**
     * 获取配置项的当前值。如果没有明确配置，则使用缺省值
     */
    default T get() {
        T value = getAssignedValue();
        if (value == null) {
            value = getDefaultValue();
        }
        return value;
    }

    default <R> Supplier<R> map(Function<T, R> fn) {
        return () -> fn.apply(get());
    }
}