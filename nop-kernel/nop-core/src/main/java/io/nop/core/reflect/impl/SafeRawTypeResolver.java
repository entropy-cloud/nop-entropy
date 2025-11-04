/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;
import io.nop.core.type.PredefinedGenericTypes;

public class SafeRawTypeResolver implements IRawTypeResolver {
    public static final SafeRawTypeResolver INSTANCE = new SafeRawTypeResolver();

    @Override
    public IGenericType resolveRawType(String className) {
        return PredefinedGenericTypes.getPredefinedType(className);
    }
}
