/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.hook;

import io.nop.api.core.annotations.core.NoReflection;

import java.util.Set;

public interface IPropGetMissingHook {
    /**
     * 返回空指针表示不明确知道扩展属性有哪些
     *
     * @return
     */
    default Set<String> prop_names() {
        return null;
    }

    /**
     * 是否允许指定名称的扩展属性
     */
    default boolean prop_allow(String propName) {
        return true;
    }

    @NoReflection
    Object prop_get(String propName);

    @NoReflection
    boolean prop_has(String propName);
}