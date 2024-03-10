/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.debugger;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class BreakpointHitMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String type;
    private StackInfo stackInfo;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public StackInfo getStackInfo() {
        return stackInfo;
    }

    public void setStackInfo(StackInfo stackInfo) {
        this.stackInfo = stackInfo;
    }
}