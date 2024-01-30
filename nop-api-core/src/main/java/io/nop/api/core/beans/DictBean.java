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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.CloneHelper;
import io.nop.api.core.util.FreezeHelper;
import io.nop.api.core.util.IDeepCloneable;
import io.nop.api.core.util.IFreezable;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.api.core.util.SourceLocation;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.api.core.util.FreezeHelper.checkNotFrozen;

@DataBean
@GraphQLObject
public class DictBean implements Serializable, IFreezable, IDeepCloneable,
        ISourceLocationGetter, ISourceLocationSetter {
    private static final long serialVersionUID = -8405107819543968894L;

    private SourceLocation location;
    private String locale;
    private String name;
    private String label;
    private String description;
    private String valueType;
    private Set<String> tagSet;

    /**
     * 前端下拉列表显示的时候有些客户要求 value-label形式显示。如果normalized=true，则表示label已经做了这样的变换，
     * 不要再进行value-label拼接。
     */
    private boolean normalized;

    private List<DictOptionBean> options = Collections.emptyList();
    private boolean frozen;

    private boolean isStatic;
    private boolean deprecated;
    private boolean internal;

    private transient Map<String, DictOptionBean> valueMap;
    private transient Map<String, DictOptionBean> labelMap;

    private Map<String, Object> attrs;

    @Override
    public boolean frozen() {
        return frozen;
    }

    @Override
    public void freeze(boolean cascade) {
        this.frozen = true;
        if (cascade) {
            this.options = FreezeHelper.freezeList(options, true);
            this.attrs = FreezeHelper.freezeMap(attrs, true);
        }
    }

    @JsonIgnore
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        checkNotFrozen(this);
        this.location = location;
    }

    @JsonAnySetter
    public void setAttr(String name, Object value) {
        checkNotFrozen(this);
        if (attrs == null)
            attrs = new LinkedHashMap<>();
        attrs.put(name, value);
    }

    @Override
    public DictBean deepClone() {
        DictBean ret = new DictBean();
        ret.setLocation(location);
        ret.setLocale(locale);
        ret.setName(name);
        ret.setLabel(label);
        ret.setDescription(description);
        ret.setValueType(valueType);
        ret.setNormalized(normalized);
        ret.setStatic(isStatic);
        ret.setInternal(internal);
        ret.setDeprecated(deprecated);
        ret.setTagSet(tagSet);
        ret.setOptions(CloneHelper.deepCloneList(options));
        ret.setAttrs(CloneHelper.deepCloneMap(attrs));
        return ret;
    }

    public boolean isNormalized() {
        return normalized;
    }

    public void setNormalized(boolean normalized) {
        checkNotFrozen(this);
        this.normalized = normalized;
    }

    @JsonIgnore
    public int getOptionCount() {
        return options.size();
    }

    @PropMeta(propId = 1)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        checkNotFrozen(this);
        this.name = name;
    }

    @PropMeta(propId = 2)
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        checkNotFrozen(this);
        this.label = label;
    }

    @PropMeta(propId = 3)
    public List<DictOptionBean> getOptions() {
        return options;
    }

    public void setOptions(List<DictOptionBean> options) {
        checkNotFrozen(this);
        this.options = options == null ? Collections.emptyList() : options;
        this.valueMap = null;
        this.labelMap = null;
    }

    public boolean containsValue(Object value) {
        return getOptionByValue(value) != null;
    }

    public boolean containsLabel(String label) {
        return getOptionByLabel(label) != null;
    }

    public DictOptionBean getOptionByValue(Object value) {
        if (valueMap == null)
            this.valueMap = makeValueMap();
        return this.valueMap.get(ConvertHelper.toString(value));
    }

    public String getLabelByValue(Object value) {
        DictOptionBean option = getOptionByValue(value);
        return option == null ? null : option.getLabel();
    }

    public DictOptionBean getOptionByLabel(String label) {
        if (labelMap == null)
            this.labelMap = makeLabelMap();
        return this.labelMap.get(label);
    }

    public Object getValueByLabel(String label) {
        DictOptionBean option = getOptionByLabel(label);
        return option == null ? null : option.getValue();
    }

    private Map<String, DictOptionBean> makeLabelMap() {
        Map<String, DictOptionBean> map = new HashMap<>();
        if (options != null) {
            for (DictOptionBean option : options) {
                map.put(option.getLabel(), option);
            }
        }
        return map;
    }

    private Map<String, DictOptionBean> makeValueMap() {
        Map<String, DictOptionBean> map = new HashMap<>();
        if (options != null) {
            for (DictOptionBean option : options) {
                Object value = option.getValue();
                map.put(ConvertHelper.toString(value), option);
            }
        }
        return map;
    }

    @JsonIgnore
    public List<String> getLabels() {
        return options.stream().map(DictOptionBean::getLabel).collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Object> getValues() {
        return options.stream().map(DictOptionBean::getValue).collect(Collectors.toList());
    }

    /**
     * 将下拉选项的label转换为value-label形式
     */
    public DictBean normalize() {
        if (normalized)
            return this;

        DictBean bean = this.deepClone();
        bean.setNormalized(true);
        for (DictOptionBean option : bean.getOptions()) {
            String label = Objects.toString(option.getLabel(), "");
            String value = Objects.toString(option.getValue(), "");
            if (value.length() > 0) {
                option.setLabel(value + '-' + label);
            }
        }
        return bean;
    }

    @PropMeta(propId = 4)
    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        checkNotFrozen(this);
        this.locale = locale;
    }

    @PropMeta(propId = 5)
    @JsonInclude(Include.NON_EMPTY)
    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        checkNotFrozen(this);
        this.valueType = valueType;
    }

    @PropMeta(propId = 6)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        checkNotFrozen(this);
        this.description = description;
    }


    @PropMeta(propId = 7)
    @JsonInclude(Include.NON_DEFAULT)
    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        checkNotFrozen(this);
        this.deprecated = deprecated;
    }

    @PropMeta(propId = 8)
    @JsonInclude(Include.NON_DEFAULT)
    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        checkNotFrozen(this);
        this.internal = internal;
    }

    @PropMeta(propId = 9)
    @JsonInclude(Include.NON_EMPTY)
    public Set<String> getTagSet() {
        return tagSet;
    }

    public void setTagSet(Set<String> tagSet) {
        checkNotFrozen(this);
        this.tagSet = tagSet;
    }

    @PropMeta(propId = 10)
    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean aStatic) {
        checkNotFrozen(this);
        isStatic = aStatic;
    }

    @PropMeta(propId = 11)
    public Map<String, Object> getAttrs() {
        return attrs;
    }

    @JsonAnyGetter
    public Map<String, Object> attrs() {
        return attrs;
    }

    public void setAttrs(Map<String, Object> attrs) {
        checkNotFrozen(this);
        this.attrs = attrs;
    }
}