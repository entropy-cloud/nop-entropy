/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.dev.beans;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class FunctionDefBean {
    private String name;
    private String description;
    private List<FunctionArgBean> args;
    private String returnType;
    private boolean macro;
    private String declaringClass;

    public String getDeclaringClass() {
        return declaringClass;
    }

    public void setDeclaringClass(String declaringClass) {
        this.declaringClass = declaringClass;
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

    public List<FunctionArgBean> getArgs() {
        return args;
    }

    public void setArgs(List<FunctionArgBean> args) {
        this.args = args;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public boolean isMacro() {
        return macro;
    }

    public void setMacro(boolean macro) {
        this.macro = macro;
    }
}