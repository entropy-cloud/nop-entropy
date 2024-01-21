/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.ioc.model._gen._BeanModel;

import java.util.HashSet;
import java.util.Set;

public class BeanModel extends _BeanModel implements IBeanModel {

    public BeanModel() {

    }

    public boolean containsTag(String tag) {
        return getIocTags() != null && getIocTags().contains(tag);
    }

    public void addName(String name) {
        Set<String> names = getName();
        if (names == null) {
            names = new HashSet<>();
            setName(names);
        }
        names.add(name);
    }

    @Override
    @JsonIgnore
    public String getBeanValueType() {
        return super.getBeanValueType();
    }
}
