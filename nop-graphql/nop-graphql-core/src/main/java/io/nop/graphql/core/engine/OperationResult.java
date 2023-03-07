/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.engine;

public class OperationResult {
    private final Object value;
    private final boolean useTry;

    public OperationResult(Object value, boolean useTry) {
        this.value = value;
        this.useTry = useTry;
    }

    public Object getValue() {
        return value;
    }

    public boolean isUseTry() {
        return useTry;
    }
}
