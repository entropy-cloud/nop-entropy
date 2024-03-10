/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.annotations.meta;

import java.lang.annotation.Annotation;

public class ObjMetaAnnotation implements ObjMeta {
    private String displayName = "";
    private String description = "";
    private int minProperties;
    private int maxProperties = Integer.MAX_VALUE;
    private boolean internal;
    private String xmlName = "";
    private boolean allowAnyAttr;
    private String validate = "";

    public void setDisplayName(String displayName) {
        if (displayName == null)
            displayName = "";
        this.displayName = displayName;
    }

    public void setDescription(String description) {
        if (description == null)
            description = "";
        this.description = description;
    }


    public void setMinProperties(int minProperties) {
        this.minProperties = minProperties;
    }

    public void setMaxProperties(int maxProperties) {
        this.maxProperties = maxProperties;
    }


    public void setInternal(boolean internal) {
        this.internal = internal;
    }


    public void setXmlName(String xmlName) {
        if (xmlName == null)
            xmlName = "";
        this.xmlName = xmlName;
    }


    public void setAllowAnyAttr(boolean allowAnyAttr) {
        this.allowAnyAttr = allowAnyAttr;
    }

    public String getValidate() {
        return validate;
    }

    public void setValidate(String validate) {
        if (validate == null)
            validate = "";
        this.validate = validate;
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
    public int minProperties() {
        return minProperties;
    }

    @Override
    public int maxProperties() {
        return maxProperties;
    }

    @Override
    public boolean internal() {
        return internal;
    }

    @Override
    public String xmlName() {
        return xmlName;
    }

    @Override
    public boolean allowAnyAttr() {
        return allowAnyAttr;
    }

    @Override
    public String validate() {
        return validate;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return ObjMeta.class;
    }
}
