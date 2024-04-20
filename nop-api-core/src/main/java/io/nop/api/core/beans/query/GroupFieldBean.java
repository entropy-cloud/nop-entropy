/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.util.ICloneable;

@DataBean
@GraphQLObject
public class GroupFieldBean implements ICloneable {
    private String owner;
    private String name;

    public static GroupFieldBean forField(String name) {
        GroupFieldBean field = new GroupFieldBean();
        field.setName(name);
        return field;
    }

    @Override
    public GroupFieldBean cloneInstance() {
        GroupFieldBean ret = new GroupFieldBean();
        ret.setOwner(owner);
        ret.setName(name);
        return ret;
    }

    @PropMeta(propId = 1)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    @PropMeta(propId = 2)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}