/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.api.core.util.Guard;
import io.nop.core.lang.eval.IEvalScope;

public class ChainedPropertyGetter implements ISpecializedPropertyGetter {
    private final ISpecializedPropertyGetter owner;
    private final ISpecializedPropertyGetter getter;

    public ChainedPropertyGetter(ISpecializedPropertyGetter owner, ISpecializedPropertyGetter getter) {
        this.owner = Guard.notNull(owner, "owner");
        this.getter = Guard.notNull(getter, "getter");
    }

    @Override
    public Object getProperty(Object obj, String propName, IEvalScope scope) {
        Object bean = owner.getProperty(obj, null, scope);
        if (bean == null)
            return null;
        return getter.getProperty(bean, null, scope);
    }
}
