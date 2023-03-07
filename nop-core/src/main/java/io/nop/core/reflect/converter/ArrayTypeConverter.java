/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.converter;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.function.Function;

public class ArrayTypeConverter implements ITypeConverter {
    private final Class<?> arrayType;

    public ArrayTypeConverter(Class<?> arrayType) {
        this.arrayType = arrayType;
    }

    @Override
    public Object convert(Object value, Function<ErrorCode, NopException> errorFactory) {
        if (value == null)
            return null;
        if (arrayType.isInstance(value))
            return value;

        if (value instanceof Collection) {
            Collection<?> c = (Collection<?>) value;
            return c.toArray((Object[]) Array.newInstance(arrayType.getComponentType(), c.size()));
        }

        return ConvertHelper.handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, arrayType, value, errorFactory);
    }
}
