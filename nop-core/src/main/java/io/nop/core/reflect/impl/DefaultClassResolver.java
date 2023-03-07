/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.impl;

import io.nop.commons.lang.IClassLoader;
import io.nop.commons.util.ClassHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultClassResolver implements IRawTypeResolver {
    public static final DefaultClassResolver INSTANCE = new DefaultClassResolver();
    static final Logger LOG = LoggerFactory.getLogger(DefaultClassResolver.class);

    @Override
    public IGenericType resolveRawType(String className) {
        IClassLoader loader = ClassHelper.getSafeClassLoader();

        Class clazz = null;
        try {
            clazz = loader.loadClass(className);
        } catch (ClassNotFoundException e) {
            LOG.debug("nop.reflect.ignore-unknown-class-when-resolve-type:class={}", className);
            return null;
        }
        return ReflectionManager.instance().buildRawType(clazz);
    }
}
