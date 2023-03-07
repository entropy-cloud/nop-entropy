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

import java.lang.reflect.Type;
import java.util.function.Function;

public class TargetTypeConverter implements ITypeConverter {
    private final Type type;
    private final ITypeConverter converter;

    public TargetTypeConverter(Type type, ITypeConverter converter) {
        this.type = type;
        this.converter = converter;
    }

    public Type getType() {
        return type;
    }

    public ITypeConverter getConverter() {
        return converter;
    }

    @Override
    public Object convert(Object value, Function<ErrorCode, NopException> errorFactory) {
        return converter.convert(value, errorFactory);
    }
}
