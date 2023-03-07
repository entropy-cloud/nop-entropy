/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.convert;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

import java.util.function.Function;

public interface ITypeConverter {
    Object convert(Object value, Function<ErrorCode, NopException> errorFactory);

    /**
     * IEvalFunction转换为java的FunctionalInterface时需要传入scope
     *
     * @param context 上下文对象，例如XScript运行时的IEvalScope对象
     */
    default <C> Object convertEx(C context, Object value, Function<ErrorCode, NopException> errorFactory) {
        return convert(value, errorFactory);
    }
}