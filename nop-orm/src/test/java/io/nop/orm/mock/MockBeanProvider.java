/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.mock;

import io.nop.api.core.ioc.IBeanProvider;

import jakarta.annotation.Nonnull;

public class MockBeanProvider implements IBeanProvider {
    @Override
    public boolean containsBean(String name) {
        return false;
    }

    @Override
    public <T> T getBeanByType(Class<T> clazz) {
        return null;
    }

    @Nonnull
    @Override
    public Object getBean(String name) {
        return null;
    }

    @Override
    public String getBeanScope(String name) {
        return null;
    }

}
