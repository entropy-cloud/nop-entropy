/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.hook;

import io.nop.api.core.annotations.core.NoReflection;

public interface IPropSetMissingHook {

    @NoReflection
    void prop_set(String propName, Object value);

    @NoReflection
    default void prop_remove(String propName) {
        prop_set(propName, null);
    }
}