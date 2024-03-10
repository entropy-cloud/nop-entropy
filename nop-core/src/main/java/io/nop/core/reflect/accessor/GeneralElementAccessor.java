/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.accessor;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IElementGetter;
import io.nop.core.reflect.IElementSetter;

import java.lang.reflect.Array;
import java.util.List;

import static io.nop.core.CoreErrors.ARG_BEAN;
import static io.nop.core.CoreErrors.ARG_INDEX;
import static io.nop.core.CoreErrors.ERR_REFLECT_BEAN_NOT_COLLECTION_FOR_GETTER;
import static io.nop.core.CoreErrors.ERR_REFLECT_BEAN_NOT_COLLECTION_FOR_SETTER;

public class GeneralElementAccessor implements IElementGetter, IElementSetter {
    public static final GeneralElementAccessor INSTANCE = new GeneralElementAccessor();

    @Override
    public Object getElement(Object bean, int index, IEvalScope scope) {
        if (bean instanceof List) {
            List<?> list = (List<?>) bean;
            if (index >= list.size())
                return null;

            return list.get(index);
        } else if (bean.getClass().isArray()) {
            int len = Array.getLength(bean);
            if (index >= len)
                return null;
            return Array.get(bean, index);
        } else {
            throw new NopException(ERR_REFLECT_BEAN_NOT_COLLECTION_FOR_GETTER).param(ARG_BEAN, bean).param(ARG_INDEX,
                    index);
        }
    }

    @Override
    public void setElement(Object obj, int index, Object value, IEvalScope scope) {
        if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            list.set(index, value);
        } else if (obj.getClass().isArray()) {
            Array.set(obj, index, value);
        } else {
            throw new NopException(ERR_REFLECT_BEAN_NOT_COLLECTION_FOR_SETTER).param(ARG_BEAN, obj).param(ARG_INDEX,
                    index);
        }
    }
}