/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import jakarta.annotation.Nonnull;

import java.io.Serializable;

import static io.nop.dao.DaoErrors.ARG_DATA_TYPE;
import static io.nop.dao.DaoErrors.ERR_DAO_INVALID_SQL_DATA_TYPE;

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

    public static SQLDataType parse(String typeName) {
        Integer precision = null, scale = null;
        int pos = typeName.indexOf('(');
        if (pos > 0) {
            if (!typeName.endsWith(")")) {
                throw new NopException(ERR_DAO_INVALID_SQL_DATA_TYPE).param(ARG_DATA_TYPE, typeName);
            }
            int pos2 = typeName.indexOf(',', pos + 1);
            if (pos2 > 0) {
                precision = ConvertHelper.toInt(typeName.substring(pos + 1, pos2).trim());
                scale = ConvertHelper.toInt(typeName.substring(pos2 + 1, typeName.length() - 1).trim());
            } else {
                precision = ConvertHelper.toInt(typeName.substring(pos + 1, typeName.length() - 1).trim());
            }
            typeName = typeName.substring(0, pos);
        }
        if (scale == null)
            scale = -1;
        if (precision == null)
            precision = -1;
        return new SQLDataType(typeName, precision, scale);
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