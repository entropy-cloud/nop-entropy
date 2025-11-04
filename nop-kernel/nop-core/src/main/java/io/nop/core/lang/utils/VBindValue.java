/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.utils;

public class VBindValue implements IBindValue {
    private final Object value;

    public VBindValue(Object value) {
        this.value = value;
    }

    @Override
    public String getBindKey(String key) {
        return ":" + key;
    }

    @Override
    public Object getBindValue() {
        return value;
    }
}