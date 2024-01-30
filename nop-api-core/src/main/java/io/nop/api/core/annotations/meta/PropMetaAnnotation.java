/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.annotations.meta;

import java.lang.annotation.Annotation;

public class PropMetaAnnotation implements PropMeta {
    private final String[] EMPTY_STRINGS = new String[0];

    private String displayName = "";
    private String description = "";
    private int propId = -1;
    private String domain = "";
    private boolean mandatory;
    private boolean internal;
    private String keyProp = "";
    private String orderProp = "";
    private String xmlName = "";
    private String childXmlName = "";
    private String childName = "";
    private String defaultOverride = "";
    private String[] depends = EMPTY_STRINGS;
    private String dict = "";
    private int precision = -1;
    private int scale = -1;

    private String stdDomain = "";
    private String pattern = "";
    private double min = Double.MIN_VALUE;
    private double max = Double.MAX_VALUE;
    private boolean excludeMin = false;
    private boolean excludeMax = false;
    private int minLength = 0;
    private int maxLength = Integer.MAX_VALUE;
    private int minItems = 0;
    private int maxItems = Integer.MAX_VALUE;
    private int multipleOf = 0;
    private String validate = "";
    private String hint = "";
    private String exportExpr = "";
    private String importExpr = "";
    private String getter = "";
    private String setter = "";
    private boolean computed;
    private boolean get;
    private boolean set;

    public boolean computed() {
        return computed;
    }

    public void setComputed(boolean computed) {
        this.computed = computed;
    }

    public boolean get() {
        return get;
    }

    public void setGet(boolean get) {
        this.get = get;
    }

    public boolean set() {
        return set;
    }

    public void setSet(boolean set) {
        this.set = set;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public int propId() {
        return propId;
    }

    @Override
    public String domain() {
        return domain;
    }

    @Override
    public String stdDomain() {
        return stdDomain;
    }

    @Override
    public boolean mandatory() {
        return mandatory;
    }

    @Override
    public boolean internal() {
        return internal;
    }

    @Override
    public String keyProp() {
        return keyProp;
    }

    @Override
    public String orderProp() {
        return orderProp;
    }

    @Override
    public String xmlName() {
        return xmlName;
    }

    @Override
    public String childXmlName() {
        return childXmlName;
    }

    @Override
    public String childName() {
        return childName;
    }

    @Override
    public String defaultOverride() {
        return defaultOverride;
    }

    @Override
    public String[] depends() {
        return depends;
    }

    @Override
    public String dict() {
        return dict;
    }

    @Override
    public int precision() {
        return precision;
    }

    @Override
    public int scale() {
        return scale;
    }

    @Override
    public String pattern() {
        return pattern;
    }

    @Override
    public double min() {
        return min;
    }

    @Override
    public double max() {
        return max;
    }

    @Override
    public boolean excludeMin() {
        return excludeMin;
    }

    @Override
    public boolean excludeMax() {
        return excludeMax;
    }

    @Override
    public int minLength() {
        return minLength;
    }

    @Override
    public int maxLength() {
        return maxLength;
    }

    @Override
    public int multipleOf() {
        return multipleOf;
    }

    @Override
    public int minItems() {
        return minItems;
    }

    @Override
    public int maxItems() {
        return maxItems;
    }

    @Override
    public String validate() {
        return validate;
    }

    @Override
    public String hint() {
        return hint;
    }

    @Override
    public String exportExpr() {
        return exportExpr;
    }

    @Override
    public String importExpr() {
        return importExpr;
    }

    @Override
    public String getter() {
        return getter;
    }

    @Override
    public String setter() {
        return setter;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return PropMeta.class;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPropId(int propId) {
        this.propId = propId;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setStdDomain(String stdDomain) {
        this.stdDomain = stdDomain;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public void setKeyProp(String keyProp) {
        this.keyProp = keyProp;
    }

    public void setOrderProp(String orderProp) {
        this.orderProp = orderProp;
    }

    public void setXmlName(String xmlName) {
        this.xmlName = xmlName;
    }

    public void setChildXmlName(String childXmlName) {
        this.childXmlName = childXmlName;
    }

    public void setChildName(String childName) {
        this.childName = childName;
    }

    public void setDefaultOverride(String defaultOverride) {
        this.defaultOverride = defaultOverride;
    }

    public void setDepends(String[] depends) {
        this.depends = depends;
    }

    public void setDict(String dict) {
        this.dict = dict;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setMin(double min) {
        this.min = min;
    }

    public void setMax(double max) {
        this.max = max;
    }

    public void setExcludeMin(boolean excludeMin) {
        this.excludeMin = excludeMin;
    }

    public void setExcludeMax(boolean excludeMax) {
        this.excludeMax = excludeMax;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public void setMinItems(int minItems) {
        this.minItems = minItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public void setMultipleOf(int multipleOf) {
        this.multipleOf = multipleOf;
    }

    public void setValidate(String validate) {
        this.validate = validate;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public void setExportExpr(String exportExpr) {
        this.exportExpr = exportExpr;
    }

    public void setImportExpr(String importExpr) {
        this.importExpr = importExpr;
    }

    public void setGetter(String getter) {
        this.getter = getter;
    }

    public void setSetter(String setter) {
        this.setter = setter;
    }
}
