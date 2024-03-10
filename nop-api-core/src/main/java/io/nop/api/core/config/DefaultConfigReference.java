/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.config;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.StaticValue;

public final class DefaultConfigReference<T> extends AbstractConfigReference<T> {
    private IConfigValue<T> provider;

    public DefaultConfigReference(SourceLocation loc, String name, Class<T> valueType, T defaultValue, IConfigValue<T> provider) {
        super(loc, name, valueType, defaultValue);
        this.provider = Guard.notNull(provider, "provider");
    }

    public DefaultConfigReference(SourceLocation loc, String name, Class<T> valueType, T defaultValue) {
        super(loc, name, valueType, defaultValue);
        this.provider = StaticValue.valueOf(defaultValue);
    }


    public void setProvider(IConfigValue<T> provider) {
        this.provider = Guard.notNull(provider, "provider");
    }

    public IConfigValue<T> getProvider() {
        return provider;
    }

    @Override
    public boolean isDynamic() {
        return provider.isDynamic();
    }

    public T getAssignedValue() {
        return provider.get();
    }

    public void updateValue(SourceLocation loc, IConfigValue<T> provider) {
        setLocation(loc);
        setProvider(provider);
    }
}