/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IPropertyGetter;

public interface ISpecializedPropertyGetter extends IPropertyGetter, ISpecializedAccessor {
    default Object getPropertyValue(Object obj) {
        return getProperty(obj, null, DisabledEvalScope.INSTANCE);
    }
}