package io.nop.rule.core.service.impl;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.VarMetaBean;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.rule.api.beans.RuleKeyBean;
import io.nop.rule.api.beans.RuleMetaBean;
import io.nop.rule.api.beans.RuleRequestBean;
import io.nop.rule.core.IRuleManager;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.model.RuleInputDefineModel;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.core.model.RuleOutputDefineModel;
import io.nop.rule.core.service.RuleServiceSpi;
import io.nop.xlang.xmeta.ISchema;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@BizModel("RuleService")
public class RuleServiceImpl implements RuleServiceSpi {

    private IRuleManager ruleManager;

    @Inject
    public void setRuleManager(IRuleManager ruleManager) {
        this.ruleManager = ruleManager;
    }

    @BizMutation
    @Override
    public java.util.Map<String, Object> executeRule(@RequestBean RuleRequestBean request,
                                                     FieldSelectionBean selection, IServiceContext ctx) {
        IRuleRuntime ruleRt = ruleManager.newRuntime(ctx.getEvalScope());
        ruleRt.setInputs(request.getInputs());
        return ruleManager.executeRule(request.getRuleName(), request.getRuleVersion(), ruleRt);
    }

    @BizQuery
    @Override
    public RuleMetaBean getRuleMeta(@RequestBean RuleKeyBean request,
                                    FieldSelectionBean selection, IServiceContext ctx) {
        RuleModel ruleModel = ruleManager.getRuleModel(request.getRuleName(), request.getRuleVersion());

        RuleMetaBean meta = new RuleMetaBean();
        meta.setRuleName(request.getRuleName());
        meta.setRuleVersion(request.getRuleVersion());
        meta.setDisplayName(ruleModel.getDisplayName());
        meta.setDescription(ruleModel.getDescription());
        List<VarMetaBean> inputs = buildInputs(ruleModel.getInputs());
        meta.setInputs(inputs);
        List<VarMetaBean> outputs = buildOutputs(ruleModel.getOutputs());
        meta.setOutputs(outputs);
        return meta;
    }

    private List<VarMetaBean> buildInputs(List<RuleInputDefineModel> inputs) {
        if (inputs == null)
            return null;

        List<VarMetaBean> ret = new ArrayList<>(inputs.size());
        for (RuleInputDefineModel input : inputs) {
            VarMetaBean var = new VarMetaBean();
            var.setMandatory(input.isMandatory());
            var.setName(input.getName());
            var.setComputed(input.isComputed());
            var.setDisplayName(input.getDisplayName());
            var.setDescription(input.getDescription());
            var.setSchema(schemaToJson(input.getSchema()));
            ret.add(var);
        }
        return ret;
    }

    private List<VarMetaBean> buildOutputs(List<RuleOutputDefineModel> outputs) {
        if (outputs == null)
            return null;

        List<VarMetaBean> ret = new ArrayList<>(outputs.size());
        for (RuleOutputDefineModel output : outputs) {
            VarMetaBean var = new VarMetaBean();
            var.setName(output.getName());
            var.setDisplayName(output.getDisplayName());
            var.setDescription(output.getDescription());
            var.setSchema(schemaToJson(output.getSchema()));
            ret.add(var);
        }
        return ret;
    }

    private Map<String, Object> schemaToJson(ISchema schema) {
        if (schema == null)
            return null;
        return (Map<String, Object>) JsonTool.serializeToJson(schema);
    }
}
