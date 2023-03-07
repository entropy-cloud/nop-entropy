/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.util.objects;

import io.nop.api.core.util.Guard;

/**
 * 标记一个类似密码、卡号的敏感数据，当它被打印到日志中时需要被替换为***。
 */
public class MaskedValue {
    private final Object value;

    public MaskedValue(Object value) {
        this.value = Guard.notNull(value, "maskedValue");
    }

    public static MaskedValue masked(Object value) {
        if (value == null)
            return null;
        return new MaskedValue(value);
    }

    public Object getValue() {
        return value;
    }
}
