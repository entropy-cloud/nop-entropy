/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.function.Consumer;

public interface IBeanPropValue {
    String getBeanValueType();

    @JsonIgnore
    default boolean isMerge() {
        return false;
    }

    default void forEachChild(Consumer<IBeanPropValue> consumer) {

    }
}
