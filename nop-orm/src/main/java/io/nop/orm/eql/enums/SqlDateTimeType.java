/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.enums;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

public enum SqlDateTimeType {
    DATE("DATE"), TIME("TIME"), TIMESTAMP("TIMESTAMP");

    private final String text;

    SqlDateTimeType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @StaticFactoryMethod
    public static SqlDateTimeType fromText(String text) {
        if (StringHelper.isEmpty(text))
            return null;

        for (SqlDateTimeType type : values()) {
            if (type.getText().equals(text))
                return type;
        }
        return null;
    }
}
