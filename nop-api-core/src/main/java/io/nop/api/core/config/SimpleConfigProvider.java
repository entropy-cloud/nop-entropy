/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.config;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.StaticValue;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.api.core.ApiErrors.ARG_SRC_TYPE;
import static io.nop.api.core.ApiErrors.ARG_TARGET_TYPE;
import static io.nop.api.core.ApiErrors.ARG_VAR;
import static io.nop.api.core.ApiErrors.ERR_CONFIG_VALUE_TYPE_NOT_ALLOW_CHANGE;
import static io.nop.api.core.ApiErrors.ERR_CONFIG_VAR_CONVERT_TO_TYPE_FAIL;

@SuppressWarnings("PMD.TooManyStaticImports")
public class SimpleConfigProvider implements IConfigProvider {
    private final Map<String, DefaultConfigReference<?>> refs = new ConcurrentHashMap<>();

    @Override
    public Map<String, DefaultConfigReference<?>> getConfigReferences() {
        return refs;
    }

    public <T> void addConfig(SourceLocation location, String name, Class<T> clazz, T value) {
        refs.put(name, new DefaultConfigReference<>(location, name, clazz, value));
    }

    @Override
    public void assignConfigValue(String name, Object value) {
        DefaultConfigReference df = getConfigRef(name, value.getClass(), null);
        value = ConvertHelper.convertTo(df.getValueType(), value, err -> new NopException(err)
                .param(ARG_VAR, name));
        df.updateValue(null, StaticValue.valueOf(value));
    }

    public <T> void updateConfigValue(IConfigReference<T> ref, T value) {
        value = ConvertHelper.convertTo(ref.getValueType(), value,
                err -> new NopException(err).param(ARG_VAR, ref.getName()));

        DefaultConfigReference<T> df = getConfigRef(ref.getName(), ref.getValueType(), ref.getDefaultValue());
        df.updateValue(ref.getLocation(), StaticValue.valueOf(value));
    }

    public void updateConfig(Map<String, IConfigReference<?>> refs) {
        for (Map.Entry<String, IConfigReference<?>> entry : refs.entrySet()) {
            IConfigReference ref = entry.getValue();
            DefaultConfigReference oldRef = getConfigRef(entry.getKey(), ref.getValueType(), ref.getDefaultValue());
            if (oldRef.getValueType() != ref.getValueType())
                throw new NopException(ERR_CONFIG_VALUE_TYPE_NOT_ALLOW_CHANGE)
                        .param(ARG_VAR, ref.getName())
                        .param(ARG_SRC_TYPE, oldRef.getValueType())
                        .param(ARG_TARGET_TYPE, ref.getValueType());
            oldRef.updateValue(ref.getLocation(), ref.getProvider());
        }
    }

    protected <T> T convertValue(String varName, Class<T> clazz, Object value) {
        if (clazz == Set.class)
            return (T) ConvertHelper.toCsvSet(value, err ->
                    new NopException(ERR_CONFIG_VAR_CONVERT_TO_TYPE_FAIL)
                            .param(ARG_VAR, varName));

        T ret = ConvertHelper.convertTo(clazz, value,
                err -> new NopException(ERR_CONFIG_VAR_CONVERT_TO_TYPE_FAIL)
                        .param(ARG_VAR, varName));
        return ret;
    }

    <T> DefaultConfigReference<T> getConfigRef(String varName, Class<T> clazz, T defaultValue) {
        return (DefaultConfigReference<T>) refs.computeIfAbsent(varName, key -> {
            String prop = System.getProperty(varName);
            T value = convertValue(varName, clazz, prop);
            DefaultConfigReference<T> valueRef = new DefaultConfigReference<>(null, varName, clazz,
                    defaultValue, StaticValue.valueOf(value));
            return valueRef;
        });
    }

    @Override
    public <T> T getConfigValue(String varName, T defaultValue) {
        DefaultConfigReference<?> ref = refs.get(varName);
        Object value;
        if (ref != null) {
            value = ref.get();
        } else {
            value = System.getProperty(varName);
        }

        if (value == null)
            return defaultValue;

        if (defaultValue != null) {
            value = convertValue(varName, defaultValue.getClass(), defaultValue);
        }
        return (T) value;
    }

    @Override
    public <T> IConfigReference<T> getConfigReference(String varName, Class<T> clazz, T defaultValue) {
        IConfigReference<?> ref = getConfigRef(varName, clazz, defaultValue);

        if (ref.getValueType() != clazz)
            throw new NopException(ERR_CONFIG_VALUE_TYPE_NOT_ALLOW_CHANGE)
                    .param(ARG_VAR, varName)
                    .param(ARG_SRC_TYPE, ref.getValueType())
                    .param(ARG_TARGET_TYPE, clazz);

        return (IConfigReference<T>) ref;
    }

    @Override
    public Runnable subscribeChange(String pattern, IConfigChangeListener listener) {
        return null;
    }
}