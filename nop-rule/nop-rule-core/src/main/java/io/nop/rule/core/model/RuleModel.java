package io.nop.rule.core.model;

import io.nop.api.core.util.INeedInit;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.model._gen._RuleModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RuleModel extends _RuleModel implements INeedInit {
    private final Map<String, RuleInputDefineModel> inputVars = new HashMap<>();
    private final Map<String, RuleOutputDefineModel> outputVars = new HashMap<>();

    private IExecutableRule executableRule;

    public RuleModel() {

    }

    /**
     * 从规则模型编译得到的可执行函数
     */
    public IExecutableRule getExecutableRule() {
        return executableRule;
    }

    public void setExecutableRule(IExecutableRule executableRule) {
        this.executableRule = executableRule;
    }

    /**
     * 根据变量名或者变量显示名获得变量定义
     *
     * @param varName 变量名或者变量显示名
     */
    public RuleInputDefineModel getInputVar(String varName) {
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

        for (RuleInputDefineModel var : getInputs()) {
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

    @Override
    public void init() {
        RuleDecisionTreeModel tree = getDecisionTree();
        if (tree != null) {
            tree.calcLeafIndex(0);
        }

        RuleDecisionMatrixModel matrix = getDecisionMatrix();
        if (matrix != null) {
            matrix.getRowDecider().calcLeafIndex(0);
            matrix.getColDecider().calcLeafIndex(0);
        }
    }
}
