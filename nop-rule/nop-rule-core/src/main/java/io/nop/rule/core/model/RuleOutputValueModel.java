package io.nop.rule.core.model;

import io.nop.rule.core.model._gen._RuleOutputValueModel;
import io.nop.xlang.xmeta.ObjVarDefineModel;

public class RuleOutputValueModel extends _RuleOutputValueModel {

    private RuleOutputDefineModel varModel;

    public RuleOutputValueModel() {

    }

    public RuleOutputDefineModel getVarModel() {
        return varModel;
    }

    public void setVarModel(RuleOutputDefineModel varModel) {
        this.varModel = varModel;
    }
}