/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type;

import java.lang.reflect.Type;

public interface IGenericTypeBuilder {
    IGenericType buildGenericType(Type type);

    IGenericType buildRawType(Class<?> clazz);
}