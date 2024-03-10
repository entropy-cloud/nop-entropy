/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.converter;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.functions.EvalFunctionalAdapter;

import java.util.function.Function;

import static io.nop.api.core.ApiErrors.ERR_CONVERT_TO_TYPE_FAIL;

public class FunctionalInterfaceConverter implements ITypeConverter {
    public static final FunctionalInterfaceConverter INSTANCE = new FunctionalInterfaceConverter();

    public static Object convertToFunctional(SourceLocation loc, IEvalFunction fn, Class<?> type,
                                             IEvalScope scope, Function<ErrorCode, NopException> errorFactory) {
        if (type.isAssignableFrom(IEvalFunction.class))
            return fn;

        EvalFunctionalAdapter adapter = new EvalFunctionalAdapter(loc, fn, scope);
        if (!type.isAssignableFrom(EvalFunctionalAdapter.class))
            ConvertHelper.handleError(ERR_CONVERT_TO_TYPE_FAIL,null, type, fn, errorFactory);

        return adapter;
    }

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