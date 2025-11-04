/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.config;

import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.StaticValue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("PMD.TooManyStaticImports")
public class SimpleConfigProvider extends AbstractConfigProvider {

    public SimpleConfigProvider(Map<String, DefaultConfigReference<?>> refs, Map<String, StaticValue<?>> staticValues) {
        super(refs, staticValues);
    }

    public SimpleConfigProvider() {
        this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    @Override
    protected <T> DefaultConfigReference<T> makeConfigRef(SourceLocation loc, String varName, Class<T> clazz) {
        return (DefaultConfigReference<T>) usedRefs.computeIfAbsent(varName, key -> {
            String prop = System.getProperty(varName);
            DefaultConfigReference<T> valueRef = new DefaultConfigReference<>(loc, varName, clazz,
                    null, StaticValue.build(varName, clazz, prop));
            return valueRef;
        });
    }

    @Override
    public Runnable subscribeChange(String pattern, IConfigChangeListener listener) {
        return null;
    }
}