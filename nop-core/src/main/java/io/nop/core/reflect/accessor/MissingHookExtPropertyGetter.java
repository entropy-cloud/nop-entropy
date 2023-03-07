/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IExtPropertyGetter;
import io.nop.core.reflect.hook.IPropGetMissingHook;

import java.util.Set;

public class MissingHookExtPropertyGetter implements IExtPropertyGetter {
    public static final MissingHookExtPropertyGetter INSTANCE = new MissingHookExtPropertyGetter();

    @Override
    public Object getProperty(Object bean, String propName, IEvalScope scope) {
        return ((IPropGetMissingHook) bean).prop_get(propName);
    }

    @Override
    public Set<String> getExtPropNames(Object obj) {
        return ((IPropGetMissingHook) obj).prop_names();
    }

    @Override
    public boolean isAllowExtProperty(Object obj, String name) {
        return ((IPropGetMissingHook) obj).prop_allow(name);
    }
}
