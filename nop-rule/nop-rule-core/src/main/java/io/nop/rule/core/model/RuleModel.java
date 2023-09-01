package io.nop.rule.core.model;

import io.nop.api.core.util.INeedInit;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.RuleConstants;
import io.nop.rule.core.model._gen._RuleModel;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.ObjMetaHelper;
import io.nop.xlang.xmeta.impl.ObjPropMetaImpl;
import io.nop.xlang.xmeta.impl.SchemaImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuleModel extends _RuleModel implements INeedInit {
    private final Map<String, RuleInputDefineModel> inputVars = new HashMap<>();
    private final Map<String, RuleOutputDefineModel> outputVars = new HashMap<>();

    private IExecutableRule executableRule;

    private ISchema inputSchema;

    public RuleModel() {

    }

    public String getRuleType() {
        if (getDecisionTree() != null && getDecisionTree().hasChildren())
            return RuleConstants.ENUM_RULE_TYPE_TREE;
        return RuleConstants.ENUM_RULE_TYPE_MATX;
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

    public String getInputVarDisplayName(String varName) {
        return ObjMetaHelper.getDisplayName(getInputSchema(), varName);
    }

    public ISchema getInputSchema() {
        if (inputSchema != null)
            return inputSchema;

        SchemaImpl schema = new SchemaImpl();
        List<ObjPropMetaImpl> props = new ArrayList<>();
        for (RuleInputDefineModel input : getInputs()) {
            ObjPropMetaImpl prop = new ObjPropMetaImpl();
            prop.setLocation(input.getLocation());
            prop.setName(input.getName());
            prop.setDisplayName(input.getDisplayName());
            prop.setSchema(input.getSchema());
            prop.setMandatory(input.isMandatory());
            props.add(prop);
        }
        schema.setProps(props);
        this.inputSchema = schema;
        return schema;
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
        initVarMap();
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
