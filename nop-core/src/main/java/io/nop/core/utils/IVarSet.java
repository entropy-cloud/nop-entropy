/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.utils;

import io.nop.core.reflect.hook.IPropGetMissingHook;

import java.util.Map;
import java.util.Set;

/**
 * 每个工作流实例都对应一个全局变量集合
 */
public interface IVarSet extends IPropGetMissingHook {
    @Override
    default Object prop_get(String propName) {
        return getVar(propName);
    }

    @Override
    default boolean prop_has(String propName) {
        return containsVar(propName);
    }

    Set<String> getVarNames();

    boolean containsVar(String varName);

    Object getVar(String varName);

    void removeVar(String varName);

    void setVar(String varName, Object value);

    void setVars(Map<String, Object> vars);
}