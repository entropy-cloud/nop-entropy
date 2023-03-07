/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IElementGetter;
import io.nop.core.reflect.IElementSetter;

import java.lang.reflect.Array;

public class ArrayElementAccessor implements IElementGetter, IElementSetter {
    public static final ArrayElementAccessor INSTANCE = new ArrayElementAccessor();

    @Override
    public Object getElement(Object obj, int index, IEvalScope scope) {
        int len = Array.getLength(obj);
        if (index >= len)
            return null;
        return Array.get(obj, index);
    }

    @Override
    public void setElement(Object obj, int index, Object value, IEvalScope scope) {
        Array.set(obj, index, value);
    }
}