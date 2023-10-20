/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.stream.core.common.state;

import java.io.Serializable;

public class StateDescriptor<T> implements Serializable {
    private final String name;
    private final Class<T> valueType;

    public StateDescriptor(String name, Class<T> valueType) {
        this.name = name;
        this.valueType = valueType;
    }

    public String getName() {
        return name;
    }

    public Class<T> getValueType() {
        return valueType;
    }
}
