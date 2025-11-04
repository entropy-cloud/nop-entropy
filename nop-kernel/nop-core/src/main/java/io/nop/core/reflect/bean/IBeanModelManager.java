/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.core.type.IGenericType;

import java.lang.reflect.Type;

public interface IBeanModelManager {
    IBeanModel getBeanModelForClass(Class<?> clazz);

    IBeanModel getBeanModelForType(IGenericType type);

    IGenericType buildGenericType(Type type);
}