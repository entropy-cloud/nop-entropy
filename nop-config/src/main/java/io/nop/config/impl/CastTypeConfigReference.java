/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.impl;

import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.config.IConfigValue;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;

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

    private T convert(Object value) {
        return ConvertHelper.convertTo(targetType, value, err -> new NopException(ERR_CONFIG_VAR_CONVERT_TO_TYPE_FAIL)
                .param(ARG_VAR, getName()).param(ARG_VALUE, value).param(ARG_TARGET_TYPE, targetType));
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
