/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalScope;

import java.util.Collection;

public class CollectionSizeGetter implements ISpecializedPropertyGetter {
    public static final CollectionSizeGetter INSTANCE = new CollectionSizeGetter();

    @Override
    public Object getProperty(Object obj, String propName, IEvalScope scope) {
        return ((Collection) obj).size();
    }
}
