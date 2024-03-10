/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type;

import io.nop.api.core.util.Guard;

public final class NonNullable<T> {
    private final T value;

    public NonNullable(T value) {
        Guard.notNull(value, "value is null");
        this.value = value;
    }

    public T get() {
        return value;
    }
}
