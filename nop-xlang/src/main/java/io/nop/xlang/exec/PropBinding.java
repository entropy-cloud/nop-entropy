/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;

public class PropBinding extends AssignIdentifier {
    private final String key;

    public PropBinding(SourceLocation loc, int slotIndex, String varName, boolean useRef,
                       IExecutableExpression initializer, String key) {
        super(loc, slotIndex, varName, useRef, initializer);
        this.key = Guard.notEmpty(key, "key is empty");
    }

    public String getKey() {
        return key;
    }
}
