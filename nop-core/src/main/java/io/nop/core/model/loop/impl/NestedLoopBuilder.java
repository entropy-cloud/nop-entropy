/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.loop.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.core.model.loop.INestedLoop;
import io.nop.core.model.loop.INestedLoopBuilder;
import io.nop.core.model.loop.model.NestedLoopModel;
import io.nop.core.model.loop.model.NestedLoopVarModel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static io.nop.core.CoreErrors.ARG_SRC_NAME;
import static io.nop.core.CoreErrors.ARG_VAR_NAME;
import static io.nop.core.CoreErrors.ERR_LOOP_SAME_VAR_AND_SRC;
import static io.nop.core.CoreErrors.ERR_LOOP_SRC_VAR_NOT_DEFINED;
import static io.nop.core.CoreErrors.ERR_LOOP_VAR_IS_ALREADY_DEFINED;

public class NestedLoopBuilder implements INestedLoopBuilder {
    private Map<String, Object> globalVars = new HashMap<>();

    private Map<String, NestedLoopVarModel> vars = new HashMap<>();

    public NestedLoopBuilder defineGlobalVar(String varName, Object varValue) {
        Guard.notEmpty(varName, "varName");

        globalVars.put(varName, varValue);
        return this;
    }

    boolean isGlobalVar(String varName) {
        return globalVars.containsKey(varName);
    }

    boolean isLoopVar(String varName) {
        return vars.containsKey(varName);
    }

    public NestedLoopBuilder defineLoopVar(String varName, String srcName, Function<?, ?> generator) {
        Guard.notEmpty(varName, "varName");
        Guard.notEmpty(srcName, "srcName");
        Guard.notNull(generator, "generator");

        if (isGlobalVar(varName) || isLoopVar(varName))
            throw new NopException(ERR_LOOP_VAR_IS_ALREADY_DEFINED).param(ARG_VAR_NAME, varName);

        if (!isGlobalVar(srcName) && !isLoopVar(srcName))
            throw new NopException(ERR_LOOP_SRC_VAR_NOT_DEFINED).param(ARG_SRC_NAME, srcName);

        if (varName.equals(srcName))
            throw new NopException(ERR_LOOP_SAME_VAR_AND_SRC).param(ARG_VAR_NAME, varName).param(ARG_SRC_NAME, srcName);

        vars.put(varName, new NestedLoopVarModel(varName, srcName, generator));
        return this;
    }

    public INestedLoop build() {
        return new NestedLoopModel(globalVars.keySet(), vars).newLoop(globalVars);
    }
}