/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.ioc;

import jakarta.annotation.Nonnull;

import static io.nop.api.core.ApiConstants.BEAN_SCOPE_PROTOTYPE;
import static io.nop.api.core.ApiConstants.BEAN_SCOPE_SINGLETON;

/**
 * 为EL表达式提供bean注入功能
 */
public interface IBeanProvider {
    boolean containsBean(String name);

    @Nonnull
    Object getBean(String name);

    @Nonnull
    <T> T getBeanByType(Class<T> clazz);

    String getBeanScope(String name);

    default boolean isSingletonScope(String name) {
        return BEAN_SCOPE_SINGLETON.equals(getBeanScope(name));
    }

    default boolean isPrototypeScope(String name) {
        return BEAN_SCOPE_PROTOTYPE.equals(getBeanScope(name));
    }
}