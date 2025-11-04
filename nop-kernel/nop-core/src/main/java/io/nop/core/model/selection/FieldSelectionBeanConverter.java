/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.selection;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;

import java.util.function.Function;

public class FieldSelectionBeanConverter implements ITypeConverter {
    @Override
    public Object convert(Object value, Function<ErrorCode, NopException> errorFactory) {
        if (StringHelper.isEmptyObject(value))
            return null;

        if (value instanceof String)
            return new FieldSelectionBeanParser().parseFromText(null, value.toString());

        if (!(value instanceof FieldSelectionBean))
            throw errorFactory.apply(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL);
        return value;
    }
}
