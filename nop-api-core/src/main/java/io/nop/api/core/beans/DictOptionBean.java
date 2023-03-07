/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;
import io.nop.api.core.util.CloneHelper;
import io.nop.api.core.util.FreezeHelper;
import io.nop.api.core.util.IDeepCloneable;
import io.nop.api.core.util.IFreezable;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.api.core.util.FreezeHelper.checkNotFrozen;

@DataBean
@GraphQLObject
public class DictOptionBean implements Serializable, IFreezable, IDeepCloneable {
    private static final long serialVersionUID = 4389354106791499447L;
    private String label;
    private Object value;
    private String description;
    private String code;
    private Map<String, Object> attrs;
    private String group;
    private boolean deprecated;
    private boolean internal;
    private boolean frozen;

    @Override
    public Object deepClone() {
        DictOptionBean ret = new DictOptionBean();
        ret.setLabel(label);
        ret.setValue(value);
        ret.setDescription(description);
        ret.setCode(code);
        ret.setGroup(group);
        ret.setDeprecated(deprecated);
        ret.setInternal(internal);
        ret.setAttrs(CloneHelper.deepCloneMap(attrs));
        return ret;
    }

    @Override
    public boolean frozen() {
        return frozen;
    }

    @Override
    public void freeze(boolean cascade) {
        frozen = true;
        if (attrs != null)
            attrs = FreezeHelper.freezeMap(attrs, true);
    }

    @JsonInclude(Include.NON_DEFAULT)
    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        checkNotFrozen(this);
        this.deprecated = deprecated;
    }

    @JsonInclude(Include.NON_DEFAULT)
    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        checkNotFrozen(this);
        this.internal = internal;
    }

    @JsonInclude(Include.NON_EMPTY)
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        checkNotFrozen(this);
        this.group = group;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        checkNotFrozen(this);
        this.label = label;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        checkNotFrozen(this);
        this.value = value;
    }

    @JsonInclude(Include.NON_EMPTY)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        checkNotFrozen(this);
        this.description = description;
    }

    @JsonInclude(Include.NON_EMPTY)
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        checkNotFrozen(this);
        this.code = code;
    }

    @JsonAnyGetter
    public Map<String, Object> getAttrs() {
        return attrs;
    }

    public void setAttrs(Map<String, Object> attrs) {
        checkNotFrozen(this);
        this.attrs = attrs;
    }

    @JsonAnySetter
    public void setAttr(String name, Object value) {
        checkNotFrozen(this);
        if (attrs == null)
            attrs = new LinkedHashMap<>();
        attrs.put(name, value);
    }
}