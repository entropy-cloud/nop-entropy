/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalScope;

import java.lang.reflect.Array;

public class ArrayLengthGetter implements ISpecializedPropertyGetter {
    public static ArrayLengthGetter INSTANCE = new ArrayLengthGetter();

    @Override
    public Object getProperty(Object obj, String propName, IEvalScope scope) {
        return Array.getLength(obj);
    }
}
