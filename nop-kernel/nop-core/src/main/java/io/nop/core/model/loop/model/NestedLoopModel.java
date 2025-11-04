/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.loop.model;

import io.nop.core.CoreConstants;
import io.nop.core.model.loop.INestedLoop;
import io.nop.core.model.loop.impl.NestedLoop;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NestedLoopModel implements Serializable {
    private static final long serialVersionUID = 3886276038302554992L;

    private final Set<String> globalVarNames;

    private final Map<String, NestedLoopVarModel> loopVars;

    public NestedLoopModel(Set<String> globalVarNames, Map<String, NestedLoopVarModel> loopVars) {
        this.globalVarNames = new HashSet<>(globalVarNames);
        this.globalVarNames.add("true");
        this.globalVarNames.add("false");
        this.loopVars = loopVars;
    }

    public boolean isGlobalVar(String varName) {
        return globalVarNames.contains(varName) || varName.equals(CoreConstants.LOOP_ROOT_VAR);
    }

    public boolean isLoopVar(String varName) {
        return loopVars.containsKey(varName);
    }

    public NestedLoopVarModel getVar(String varName) {
        return loopVars.get(varName);
    }

    public Set<String> getGlobalVarNames() {
        return globalVarNames;
    }

    public Map<String, NestedLoopVarModel> getLoopVars() {
        return loopVars;
    }

    public INestedLoop newLoop(Map<String, Object> globalVars) {
        return new NestedLoop(this, globalVars);
    }
}