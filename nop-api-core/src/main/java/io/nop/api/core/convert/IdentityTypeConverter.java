/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.convert;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

import java.util.function.Function;

public final class IdentityTypeConverter implements ITypeConverter {
    public static final IdentityTypeConverter INSTANCE = new IdentityTypeConverter();

    @Override
    public Object convert(Object value, Function<ErrorCode, NopException> errorFactory) {
        return value;
    }
}
