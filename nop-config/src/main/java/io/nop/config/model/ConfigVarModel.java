/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.model;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.api.core.util.SourceLocation;

import java.util.Map;

@DataBean
public class ConfigVarModel implements ISourceLocationSetter, ISourceLocationGetter {
    private SourceLocation location;
    private String name;
    private String displayName;
    private String description;
    private Class<?> valueType;
    private Object defaultValue;
    private String since;
    private boolean deprecated;
    private boolean internal;

    /**
     * 嵌套定义
     */
    private Map<String, ConfigVarModel> vars;

    public String getSince() {
        return since;
    }

    public void setSince(String since) {
        this.since = since;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Map<String, ConfigVarModel> getVars() {
        return vars;
    }

    public void setVars(Map<String, ConfigVarModel> vars) {
        this.vars = vars;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Class<?> getValueType() {
        return valueType;
    }

    public void setValueType(Class<?> valueType) {
        this.valueType = valueType;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }
}