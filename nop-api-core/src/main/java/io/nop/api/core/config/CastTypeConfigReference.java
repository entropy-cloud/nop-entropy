/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.config;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.SourceLocation;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.nop.api.core.ApiErrors.ARG_TARGET_TYPE;
import static io.nop.api.core.ApiErrors.ARG_VALUE;
import static io.nop.api.core.ApiErrors.ARG_VAR;
import static io.nop.api.core.ApiErrors.ERR_CONFIG_VAR_CONVERT_TO_TYPE_FAIL;

public class CastTypeConfigReference<T> implements IConfigReference<T> {
    private final IConfigReference<?> ref;
    private final Class<T> targetType;

    public CastTypeConfigReference(IConfigReference<?> ref, Class<T> targetType) {
        this.ref = ref;
        this.targetType = targetType;
    }

    @Override
    public IConfigValue<T> getProvider() {
        return this;
    }

    @Override
    public boolean isDynamic() {
        return ref.isDynamic();
    }

    @Override
    public String getName() {
        return ref.getName();
    }

    @Override
    public Class<T> getValueType() {
        return targetType;
    }

    @Override
    public T getDefaultValue() {
        return convert(ref.getDefaultValue());
    }

    @SuppressWarnings("unchecked")
    private T convert(Object value) {
        if (value instanceof String) {
            if (targetType == List.class || targetType == Collection.class)
                return (T) ApiStringHelper.stripedSplit(value.toString(), ',');
            if (targetType == Set.class)
                return (T) new LinkedHashSet<>(ApiStringHelper.stripedSplit(value.toString(), ','));
        }
        return ConvertHelper.convertTo(targetType, value, err -> this.newError(value));
    }

    private NopException newError(Object value) {
        return new NopException(ERR_CONFIG_VAR_CONVERT_TO_TYPE_FAIL)
                .param(ARG_VAR, getName()).param(ARG_VALUE, value).param(ARG_TARGET_TYPE, targetType);
    }

    @Override
    public T getAssignedValue() {
        return convert(ref.getAssignedValue());
    }

    @Override
    public SourceLocation getLocation() {
        return ref.getLocation();
    }
}
