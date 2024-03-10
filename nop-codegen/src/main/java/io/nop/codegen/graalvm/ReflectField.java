/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.graalvm;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class ReflectField {
    private String name;

    /**
     * 只有明确配置了allowUnsafeAccess之后才能使用Unsafe.objectFieldOffset
     */
    private boolean allowUnsafeAccess;

    private boolean allowWrite;

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isAllowUnsafeAccess() {
        return allowUnsafeAccess;
    }

    public void setAllowUnsafeAccess(boolean allowUnsafeAccess) {
        this.allowUnsafeAccess = allowUnsafeAccess;
    }

    public boolean isAllowWrite() {
        return allowWrite;
    }

    public void setAllowWrite(boolean allowWrite) {
        this.allowWrite = allowWrite;
    }

    public void merge(ReflectField field) {
        this.allowUnsafeAccess = this.allowUnsafeAccess || field.allowUnsafeAccess;
        this.allowWrite = this.allowWrite || field.allowWrite;
    }
}
