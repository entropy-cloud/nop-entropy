/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IPropertySetter;

public interface ISpecializedPropertySetter extends IPropertySetter, ISpecializedAccessor {
    default void setPropertyValue(Object obj, Object value) {
        setProperty(obj, null, value, DisabledEvalScope.INSTANCE);
    }
}
