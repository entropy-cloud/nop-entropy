/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.converter;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;

import java.util.Map;
import java.util.function.Function;

public class DataBeanTypeConverter implements ITypeConverter {
    private final IGenericType typeInfo;

    public DataBeanTypeConverter(IGenericType typeInfo) {
        this.typeInfo = typeInfo;
    }

    @Override
    public Object convert(Object value, Function<ErrorCode, NopException> errorFactory) {
        if (value == null)
            return null;
        if (typeInfo.isInstance(value))
            return value;
        if (value instanceof Map) {
            return BeanTool.instance().buildBean(value, typeInfo, null);
        }
        return ConvertHelper.handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, typeInfo.getRawClass(), value, errorFactory);
    }
}
