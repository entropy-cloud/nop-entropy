/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IFreezable;

import java.io.Serializable;

import static io.nop.core.CoreErrors.ARG_MODEL;
import static io.nop.core.CoreErrors.ERR_REFLECT_MODEL_IS_READONLY;

public class ReadonlyModel implements Serializable, IFreezable {

    /**
     * 标记为只读对象之后不再允许修改
     */
    private boolean frozen;

    /**
     * 在调试模式下，每次调用反射函数都会更新调用数统计，用于跟踪哪些函数实际被执行了
     */
    private long callCount;

    private long accessCount;

    public long getCallCount() {
        return callCount;
    }

    public void incCallCount() {
        this.callCount++;
    }

    public long getAccessCount() {
        return accessCount;
    }

    public void incAccessCount() {
        this.accessCount++;
    }

    public boolean frozen() {
        return frozen;
    }

    public void freeze(boolean cascade) {
        if (frozen)
            return;

        this.frozen = true;
    }

    protected void checkReadonly() {
        if (frozen)
            throw new NopException(ERR_REFLECT_MODEL_IS_READONLY).param(ARG_MODEL, this);
    }
}
