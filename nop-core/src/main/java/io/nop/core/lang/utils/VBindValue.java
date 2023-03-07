/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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