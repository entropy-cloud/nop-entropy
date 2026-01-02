/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
import io.nop.rule.api.beans.RuleResultBean;
import io.nop.rule.core.IRuleManager;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.RuleConstants;
import io.nop.rule.core.model.RuleInputDefineModel;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.core.model.RuleOutputDefineModel;
import io.nop.rule.core.service.IRuleLogMessageSaver;
import io.nop.rule.core.service.RuleServiceSpi;
import io.nop.xlang.xmeta.ISchema;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@BizModel("RuleService")
public class RuleServiceImpl implements RuleServiceSpi {

    private IRuleManager ruleManager;
    private boolean saveLogMessage;
    private IRuleLogMessageSaver logMessageSaver;

    public void setLogMessageSaver(IRuleLogMessageSaver ruleLogMessageSaver) {
        this.logMessageSaver = ruleLogMessageSaver;
    }

    public void setSaveLogMessage(boolean saveLogMessage) {
        this.saveLogMessage = saveLogMessage;
    }

    @Inject
    public void setRuleManager(IRuleManager ruleManager) {
        this.ruleManager = ruleManager;
    }

    @BizMutation
    @Override
    public RuleResultBean executeRule(@RequestBean RuleRequestBean request,
                                      FieldSelectionBean selection, IServiceContext ctx) {
        IRuleRuntime ruleRt = ruleManager.newRuleRuntime(ctx, ctx.getEvalScope());
        ruleRt.setInputs(request.getInputs());
        ruleRt.setRuleName(request.getRuleName());
        ruleRt.setRuleVersion(request.getRuleVersion());
        if (selection != null && selection.hasSourceField(RuleConstants.FIELD_LOG_MESSAGES)) {
            ruleRt.setCollectLogMessage(true);
        }

        Map<String, Object> outputs = ruleManager.executeRule(request.getRuleName(), request.getRuleVersion(), ruleRt);
        RuleResultBean ret = new RuleResultBean();
        ret.setRuleName(ruleRt.getRuleName());
        ret.setRuleVersion(ruleRt.getRuleVersion());
        ret.setOutputs(outputs);
        ret.setRuleMatch(ruleRt.isRuleMatch());
        if (ruleRt.isCollectLogMessage()) {
            ret.setLogMessages(ruleRt.getLogMessages());

            if (saveLogMessage && logMessageSaver != null)
                logMessageSaver.saveLogMessages(ruleRt.getLogMessages(), ruleRt);
        }

        return ret;
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
        return (Map<String, Object>) JsonTool.beanToJsonObject(schema);
    }
}
