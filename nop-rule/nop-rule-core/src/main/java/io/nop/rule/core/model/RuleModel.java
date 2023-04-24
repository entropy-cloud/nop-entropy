package io.nop.rule.core.model;

import io.nop.rule.core.model._gen._RuleModel;
import io.nop.xlang.xmeta.ObjVarDefineModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RuleModel extends _RuleModel {
    private final Map<String, ObjVarDefineModel> inputVars = new HashMap<>();
    private final Map<String, RuleOutputDefineModel> outputVars = new HashMap<>();

    public RuleModel() {

    }

    public ObjVarDefineModel getInputVar(String varName) {
        return inputVars.get(varName);
    }

    public RuleOutputDefineModel getOutputVar(String varName) {
        return outputVars.get(varName);
    }

    public void initVarMap() {
        if (getInputs() == null)
            setInputs(new ArrayList<>());

        if (getOutputs() == null)
            setOutputs(new ArrayList<>());

        for (ObjVarDefineModel var : getInputs()) {
            inputVars.put(var.getName(), var);
            if (var.getDisplayName() != null) {
                inputVars.put(var.getDisplayName(), var);
            }
        }

        for (RuleOutputDefineModel var : getOutputs()) {
            outputVars.put(var.getName(), var);
            if (var.getDisplayName() != null) {
                outputVars.put(var.getDisplayName(), var);
            }
        }
    }
}
