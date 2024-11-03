/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.utils;

import io.nop.api.core.util.Guard;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MapVarSet implements IVarSet {
    private final Map<String, Object> vars;

    public MapVarSet(Map<String, Object> vars) {
        this.vars = Guard.notNull(vars, "vars");
    }

    public MapVarSet() {
        this(new ConcurrentHashMap<>());
    }

    @Override
    public Set<String> getVarNames() {
        return vars.keySet();
    }

    @Override
    public Object getVar(String varName) {
        return vars.get(varName);
    }

    @Override
    public void removeVar(String varName) {
        vars.remove(varName);
    }

    @Override
    public void setVar(String varName, Object value) {
        this.vars.put(varName, value);
    }

    @Override
    public void setVars(Map<String, Object> vars) {
        if (vars != null)
            this.vars.putAll(vars);
    }

    @Override
    public boolean containsVar(String varName) {
        return vars.containsKey(varName);
    }
}
