/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICloneable;

import java.io.Serializable;

@DataBean
@GraphQLObject
@SuppressWarnings("PMD.UselessParentheses")
public class OrderFieldBean implements Serializable, ICloneable {
    private static final long serialVersionUID = -6865693009305659956L;
    private String owner;

    private String name;

    // 缺省为升序
    private boolean desc;
    private Boolean nullsFirst;

    public String toString() {
        Boolean nullsFirst = getNullsFirst();
        return (owner == null ? "" : owner + ".") + name + " " + (desc ? "desc" : "asc") +
                (nullsFirst != null ? (getNullsFirst() ? " nulls first" : " nulls last") : "");
    }

    public static OrderFieldBean desc(String name) {
        return orderBy(name, false);
    }

    public static OrderFieldBean asc(String name) {
        return orderBy(name, true);
    }

    public static OrderFieldBean orderBy(String name, boolean asc) {
        return forField(name, asc);
    }

    public static OrderFieldBean forField(String name, boolean desc) {
        OrderFieldBean ret = new OrderFieldBean();
        ret.setName(name);
        ret.setDesc(desc);
        return ret;
    }

    public static OrderFieldBean forField(String name) {
        return forField(name, false);
    }

    public static OrderFieldBean fromTreeBean(ITreeBean tree) {
        OrderFieldBean ret = new OrderFieldBean();
        ret.setName((String) tree.getAttr("name"));
        ret.setDesc(ConvertHelper.toPrimitiveBoolean(tree.getAttr("desc")));
        ret.setNullsFirst(ConvertHelper.toBoolean(tree.getAttr("nullsFirst")));
        return ret;
    }

    @Override
    public OrderFieldBean cloneInstance() {
        OrderFieldBean ret = new OrderFieldBean();
        ret.setOwner(owner);
        ret.setName(name);
        ret.setDesc(!desc);
        ret.setNullsFirst(nullsFirst);
        return ret;
    }

    public OrderFieldBean reverse() {
        OrderFieldBean ret = new OrderFieldBean();
        ret.setOwner(owner);
        ret.setName(name);
        ret.setDesc(!desc);
        if (nullsFirst != null)
            ret.setNullsFirst(!nullsFirst);
        return ret;
    }

    @PropMeta(propId = 1)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Guard.notEmpty(name, "name");
    }

    @PropMeta(propId = 2)
    public boolean isDesc() {
        return desc;
    }

    public void setDesc(boolean desc) {
        this.desc = desc;
    }

    @JsonIgnore
    public boolean isAsc() {
        return !desc;
    }

    @PropMeta(propId = 3)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    @PropMeta(propId = 4)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getNullsFirst() {
        return nullsFirst;
    }

    public boolean shouldNullsFirst() {
        if (nullsFirst != null)
            return nullsFirst;
        return !desc;
    }

    public void setNullsFirst(Boolean value) {
        nullsFirst = value;
    }

}