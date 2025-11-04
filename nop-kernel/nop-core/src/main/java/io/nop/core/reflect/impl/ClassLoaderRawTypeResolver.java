/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.lang.IClassLoader;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;

public class ClassLoaderRawTypeResolver implements IRawTypeResolver {
    private final IClassLoader classLoader;

    public ClassLoaderRawTypeResolver(IClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public IGenericType resolveRawType(String typeName) {
        Class clazz;
        try {
            clazz = classLoader.loadClass(typeName);
        } catch (ClassNotFoundException e) {
            throw NopException.adapt(e);
        }
        return ReflectionManager.instance().buildRawType(clazz);
    }
}
