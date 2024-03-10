/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval;

import io.nop.api.core.util.SourceLocation;

public class FrameVariable {
    private final SourceLocation location; // 变量最后一次赋值的位置
    private final String name;
    private final Object value;
    private final int slot;

    public FrameVariable(int slot, String name, SourceLocation location, Object value) {
        this.slot = slot;
        this.location = location;
        this.name = name;
        this.value = value;
    }

    public int getSlot() {
        return slot;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }
}
