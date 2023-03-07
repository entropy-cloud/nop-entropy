/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.loop;

import io.nop.commons.util.objects.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public interface INestedLoopVar extends INestedLoopSupport {
    String getVarName();

    /**
     * 循环变量的值
     */
    Object getVarValue();

    // Map<String, Object> getParentVars();

    INestedLoopVar getParentVar();

    default INestedLoopVar getVar(String name) {
        if (getVarName().equals(name))
            return this;
        return getParentVar(name);
    }

    default boolean hasVar(String name) {
        return getVar(name) != null;
    }

    default INestedLoopVar getParentVar(String name) {
        INestedLoopVar parent = getParentVar();
        while (parent != null) {
            if (parent.getVarName().equals(name))
                return parent;
            parent = parent.getParentVar();
        }
        return null;
    }

    default void forSelfAndParents(Consumer<INestedLoopVar> action) {
        action.accept(this);
        INestedLoopVar parent = getParentVar();
        while (parent != null) {
            action.accept(parent);
            parent = parent.getParentVar();
        }
    }

    default List<Pair<String, Object>> getLoopValues() {
        List<Pair<String, Object>> list = new ArrayList<>();
        list.add(Pair.of(getVarName(), getVarValue()));
        INestedLoopVar parent = getParentVar();
        while (parent != null) {
            list.add(Pair.of(parent.getVarName(), parent.getVarValue()));
            parent = parent.getParentVar();
        }
        return list;
    }
}