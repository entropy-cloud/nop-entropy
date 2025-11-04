/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.converter;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.xml.XNode;

import java.util.function.Function;

public class XNodeTypeConverter implements ITypeConverter {
    public static final XNodeTypeConverter INSTANCE = new XNodeTypeConverter();

    @Override
    public Object convert(Object value, Function<ErrorCode, NopException> errorFactory) {
        if (value == null)
            return null;

        if (value instanceof XNode)
            return value;

        if (value instanceof ITreeBean) {
            return XNode.fromTreeBean((ITreeBean) value);
        }
        return ConvertHelper.handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, XNode.class, value, errorFactory);
    }
}
