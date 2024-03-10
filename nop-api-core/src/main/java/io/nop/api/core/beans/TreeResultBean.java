/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;
import io.nop.api.core.annotations.meta.PropMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@DataBean
@GraphQLObject
public class TreeResultBean {
    private String value;
    private String label;

    private Map<String, Object> attrs;

    private List<TreeResultBean> children;

    @PropMeta(propId = 1)
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @PropMeta(propId = 2)
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @PropMeta(propId = 3)
    public List<TreeResultBean> getChildren() {
        return children;
    }

    public void setChildren(List<TreeResultBean> children) {
        this.children = children;
    }

    @PropMeta(propId = 4)
    public Map<String, Object> getAttrs() {
        return attrs;
    }

    public Object getAttr(String name) {
        if (attrs == null)
            return null;
        return attrs.get(name);
    }

    public void setAttr(String name, Object value) {
        if (value == null) {
            if (attrs != null)
                attrs.remove(name);
        } else {
            if (attrs == null)
                attrs = new LinkedHashMap<>();
            attrs.put(name, value);
        }
    }
}