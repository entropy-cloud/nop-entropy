/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.hook.IPropMakeMissingHook;

public class MissingHookExtPropertyMaker implements IPropertyGetter {
    public static MissingHookExtPropertyMaker INSTANCE = new MissingHookExtPropertyMaker();

    @Override
    public Object getProperty(Object bean, String propName, IEvalScope scope) {
        return ((IPropMakeMissingHook) bean).prop_make(propName);
    }
}
