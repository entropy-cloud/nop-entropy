/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.graalvm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;

import java.util.Collections;
import java.util.List;

@DataBean
public class ReflectMethod implements Comparable<ReflectMethod> {
    private String name;
    private List<String> parameterTypes = Collections.emptyList();

    @Override
    public int compareTo(ReflectMethod o) {
        int cmp = name.compareTo(o.name);
        if (cmp != 0)
            return cmp;

        return toParamString().compareTo(o.toParamString());
    }

    public String toParamString() {
        return StringHelper.join(parameterTypes, ",");
    }

    @JsonIgnore
    public String getSignature() {
        return name + "(" + StringHelper.join(parameterTypes, ",") + ")";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(List<String> parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public void merge(ReflectMethod method) {

    }
}
