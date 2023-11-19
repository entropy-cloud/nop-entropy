package io.nop.wf.core.store.beans;

import io.nop.api.core.util.Guard;
import io.nop.wf.core.IWorkflowVarSet;

import java.util.Map;
import java.util.Set;

public class MapVarSet implements IWorkflowVarSet {
    private final Map<String, Object> vars;

    public MapVarSet(Map<String, Object> vars) {
        this.vars = Guard.notNull(vars, "vars");
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
}
