/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.enums;

import io.nop.api.core.annotations.core.StaticFactoryMethod;

public enum SqlWindowFrameBoundType {
    UNBOUNDED_PRECEDING, PRECEDING, CURRENT_ROW, FOLLOWING, UNBOUNDED_FOLLOWING;

    @StaticFactoryMethod
    public static SqlWindowFrameBoundType fromText(String text) {
        for (SqlWindowFrameBoundType type : values()) {
            if (type.name().equalsIgnoreCase(text))
                return type;
        }
        return null;
    }
}
