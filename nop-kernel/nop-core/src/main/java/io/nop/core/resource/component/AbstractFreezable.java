/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IFreezable;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.api.core.util.SourceLocation;

import java.io.Serializable;

import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_COMPONENT_NOT_ALLOW_CHANGE;

public abstract class AbstractFreezable
        implements Serializable, IFreezable, ISourceLocationGetter, ISourceLocationSetter {
    private boolean frozen;
    private SourceLocation location;

    public String toString() {
        return getClass().getSimpleName() + "[loc=" + getLocation() + "]";
    }

    @Override
    public boolean frozen() {
        return frozen;
    }

    protected void checkAllowChange() {
        if (frozen)
            throw new NopException(ERR_COMPONENT_NOT_ALLOW_CHANGE).param(ARG_RESOURCE_PATH, resourcePath());
    }

    @Override
    public void freeze(boolean cascade) {
        this.frozen = true;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Override
    public SourceLocation getLocation() {
        return location;
    }

    @Override
    public void setLocation(SourceLocation location) {
        checkAllowChange();
        this.location = location;
    }
}
