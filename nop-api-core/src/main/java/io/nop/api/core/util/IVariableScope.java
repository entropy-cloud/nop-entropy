/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.util;

public interface IVariableScope {
    default boolean containsValue(String name) {
        return getValue(name) != null;
    }

    Object getValue(String name);

    Object getValueByPropPath(String propPath);
}