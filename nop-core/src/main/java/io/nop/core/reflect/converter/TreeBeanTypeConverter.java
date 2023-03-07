/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.converter;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

import java.util.function.Function;

public class TreeBeanTypeConverter implements ITypeConverter {
    public static final TreeBeanTypeConverter INSTANCE = new TreeBeanTypeConverter();

    @Override
    public Object convert(Object value, Function<ErrorCode, NopException> errorFactory) {
        if (value == null)
            return null;

        if (value instanceof TreeBean)
            return value;

        if (value instanceof ITreeBean) {
            return TreeBean.fromTreeBean((ITreeBean) value);
        }
        return ConvertHelper.handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, TreeBean.class, value, errorFactory);
    }
}
