/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.converter;

import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.functions.EvalFunctionalAdapter;

import java.util.function.Function;

public class FunctionalInterfaceConverter implements ITypeConverter {
    public static final FunctionalInterfaceConverter INSTANCE = new FunctionalInterfaceConverter();

    @Override
    public Object convert(Object value, Function<ErrorCode, NopException> errorFactory) {
        return convertEx(DisabledEvalScope.INSTANCE, value, errorFactory);
    }

    @Override
    public <C> Object convertEx(C context, Object value, Function<ErrorCode, NopException> errorFactory) {
        if (value == null)
            return null;

        if (value instanceof IEvalFunction) {
            if (value instanceof EvalFunctionalAdapter)
                return value;
            IEvalFunction fn = (IEvalFunction) value;
            return new EvalFunctionalAdapter(null, fn, (IEvalScope) context);
        }

        return value;
    }
}