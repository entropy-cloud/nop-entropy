/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dialect;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.data.ImmutableBean;

import jakarta.annotation.Nonnull;
import java.io.Serializable;

@DataBean
@ImmutableBean
public class SQLDataType implements Serializable {
    private static final long serialVersionUID = -4070047806460630447L;

    private final String name;
    private final int precision;
    private final int scale;

    public SQLDataType(@Nonnull @JsonProperty("name") String name, @JsonProperty("precision") int precision,
                       @JsonProperty("scale") int scale) {
        this.name = name.toUpperCase();
        this.precision = precision;
        this.scale = scale;
    }

    public String toString() {
        if (!isAllowPrecision() && !isAllowScale())
            return name;

        if (!isAllowScale()) {
            if (precision == 0)
                return name;

            return name + "(" + precision + ")";
        }
        return name + "(" + precision + "," + scale + ")";
    }

    public boolean isAllowPrecision() {
        return precision >= 0;
    }

    public boolean isAllowScale() {
        return scale >= 0;
    }

    public String getName() {
        return name;
    }

    public int getPrecision() {
        return precision;
    }

    public int getScale() {
        return scale;
    }
}