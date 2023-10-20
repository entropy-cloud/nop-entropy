/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.router;

import java.util.List;

public class RouteValue<V> {
    private final List<String> varNames;
    private final V value;

    public RouteValue(List<String> varNames, V value) {
        this.varNames = varNames;
        this.value = value;
    }

    public List<String> getVarNames() {
        return varNames;
    }

    public V getValue() {
        return value;
    }
}
