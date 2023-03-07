/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval.global;

import io.nop.api.core.annotations.core.Description;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;

public class StaticClassGlobalVariableDefinition implements IGlobalVariableDefinition {
    private final IGenericType type;
    private final Class<?> clazz;

    public StaticClassGlobalVariableDefinition(Class<?> clazz) {
        this.clazz = clazz;
        this.type = ReflectionManager.instance().buildGenericClassType(clazz);
    }

    @Override
    public String getDescription() {
        Description desc = clazz.getAnnotation(Description.class);
        if (desc != null)
            return desc.value();
        return null;
    }

    @Override
    public IGenericType getResolvedType() {
        return type;
    }

    @Override
    public Object getValue(IEvalScope scope) {
        return clazz;
    }
}
