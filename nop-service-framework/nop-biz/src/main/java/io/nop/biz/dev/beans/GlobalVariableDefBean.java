/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.dev.beans;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class GlobalVariableDefBean {
    private String name;
    private String description;
    private String type;
    private boolean staticClass;

    private List<FunctionDefBean> methods;

    public boolean isStaticClass() {
        return staticClass;
    }

    public void setStaticClass(boolean staticClass) {
        this.staticClass = staticClass;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<FunctionDefBean> getMethods() {
        return methods;
    }

    public void setMethods(List<FunctionDefBean> methods) {
        this.methods = methods;
    }
}