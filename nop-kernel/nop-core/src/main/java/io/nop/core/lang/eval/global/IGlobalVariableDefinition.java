/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval.global;

import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.type.IGenericType;

public interface IGlobalVariableDefinition {
    default String getDescription() {
        return null;
    }

    IGenericType getResolvedType();

    Object getValue(EvalRuntime rt);
}