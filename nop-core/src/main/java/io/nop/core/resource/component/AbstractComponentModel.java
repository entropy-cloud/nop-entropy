/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.component;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.IFreezable;
import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.IJsonSerializable;
import io.nop.core.reflect.hook.SerializableExtensibleObject;

import java.util.Set;

import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_COMPONENT_NOT_ALLOW_CHANGE;

public abstract class AbstractComponentModel extends SerializableExtensibleObject
        implements IComponentModel, IFreezable, ISourceLocationSetter, IJsonSerializable {
    private boolean frozen;
    private SourceLocation location;

    public String toString() {
        return getClass().getSimpleName() + "[loc=" + getLocation() + "]";
    }

    @Override
    public boolean frozen() {
        return frozen;
    }

    @Override
    protected void checkAllowChange() {
        if (frozen)
            throw new NopException(ERR_COMPONENT_NOT_ALLOW_CHANGE).param(ARG_RESOURCE_PATH, resourcePath());
    }

    @Override
    public void freeze(boolean cascade) {
        this.frozen = true;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        checkAllowChange();
        this.location = location;
    }

    @Override
    public void serializeToJson(IJsonHandler out) {
        out.beginObject(getLocation());
        outputJson(out);
        out.endObject();
    }

    protected void outputJson(IJsonHandler handler) {
        if (location != null)
            handler.put("location", location);

        Set<String> names = prop_names();
        for (String name : names) {
            handler.put(name, getExtProp(name));
        }
    }
}
