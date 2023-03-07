/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval.global;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;

@Locale("zh-CN")
@Description("全局上下文对象")
public class ContextGlobalVariable implements IGlobalVariableDefinition {
    private final IGenericType type = ReflectionManager.instance().buildRawType(IContext.class);

    @Override
    public IGenericType getResolvedType() {
        return type;
    }

    @Override
    public Object getValue(IEvalScope scope) {
        return ContextProvider.instance().currentContext();
    }
}
