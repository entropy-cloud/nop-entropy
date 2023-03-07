/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.reflect.hook.IPropSetMissingHook;

public class MissingHookExtPropertySetter implements IPropertySetter {
    public static MissingHookExtPropertySetter INSTANCE = new MissingHookExtPropertySetter();

    @Override
    public void setProperty(Object bean, String propName, Object value, IEvalScope scope) {
        ((IPropSetMissingHook) bean).prop_set(propName, value);
    }
}
