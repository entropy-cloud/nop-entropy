/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.enums;

import io.nop.api.core.annotations.core.StaticFactoryMethod;

public enum SqlIntervalUnit {
    MICROSECOND, SECOND, MINUTE, HOUR, DAY, WEEK, MONTH, QUARTER, YEAR;

    @StaticFactoryMethod
    public static SqlIntervalUnit fromText(String text) {
        for (SqlIntervalUnit unit : values()) {
            if (unit.name().equalsIgnoreCase(text))
                return unit;
        }
        return null;
    }
}
