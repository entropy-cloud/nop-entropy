/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IElementGetter;
import io.nop.core.reflect.IElementSetter;

import java.util.List;

public class ListElementAccessor implements IElementGetter, IElementSetter {
    public static final ListElementAccessor INSTANCE = new ListElementAccessor();

    @Override
    public Object getElement(Object bean, int index, IEvalScope scope) {
        List<?> list = (List<?>) bean;
        if (index >= list.size())
            return null;

        return list.get(index);
    }

    @Override
    public void setElement(Object obj, int index, Object value, IEvalScope scope) {
        List<Object> list = (List) obj;
        list.set(index, value);
    }
}