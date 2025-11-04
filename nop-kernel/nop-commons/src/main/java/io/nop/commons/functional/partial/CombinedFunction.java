/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.functional.partial;

import io.nop.api.core.exceptions.NopMissingHandlerException;

import java.util.List;

import static io.nop.api.core.util.Guard.notNull;
import static io.nop.commons.CommonErrors.ARG_CLASS;
import static io.nop.commons.CommonErrors.ERR_PARTIAL_FUNCTION_NOT_DEFINED;

public class CombinedFunction<A, B> implements IPartialFunction<A, B> {
    private final List<IPartialFunction<A, B>> functions;

    public CombinedFunction(List<IPartialFunction<A, B>> functions) {
        this.functions = notNull(functions, "functions is null");
    }

    @Override
    public boolean isDefinedAt(A a) {
        for (IPartialFunction<A, B> fn : functions) {
            if (fn.isDefinedAt(a))
                return true;
        }
        return false;
    }

    @Override
    public B apply(A a) {
        for (IPartialFunction<A, B> fn : functions) {
            if (fn.isDefinedAt(a))
                return fn.apply(a);
        }
        throw new NopMissingHandlerException(ERR_PARTIAL_FUNCTION_NOT_DEFINED).param(ARG_CLASS,
                a == null ? null : a.getClass());
    }
}