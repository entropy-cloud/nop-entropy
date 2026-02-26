/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.core;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xt.IXTransformRule;
import io.nop.xlang.xt.model.*;
import io.nop.xlang.xt.rules.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XtTransformCompiler {

    public IXTransformRule compile(XtTransformModel model) {
        return compileRuleGroup(model.getMain());
    }

    public Map<String, IXTransformRule> compileTemplates(XtTransformModel model) {
        Map<String, IXTransformRule> templates = new HashMap<>();
        if (model.getTemplates() != null) {
            for (XtTemplateModel template : model.getTemplates()) {
                templates.put(template.getId(), compileRuleGroup(template));
            }
        }
        return templates;
    }

    public Map<String, XtMappingModel> getMappings(XtTransformModel model) {
        Map<String, XtMappingModel> mappings = new HashMap<>();
        if (model.getMappings() != null) {
            for (XtMappingModel mapping : model.getMappings()) {
                mappings.put(mapping.getId(), mapping);
            }
        }
        return mappings;
    }

    public IXTransformRule compileRuleGroup(XtRuleGroupModel group) {
        if (group == null || group.getBody() == null || group.getBody().isEmpty()) {
            return null;
        }

        List<IXTransformRule> rules = new ArrayList<>();
        for (XtRuleModel rule : group.getBody()) {
            IXTransformRule compiled = compileRule(rule);
            if (compiled != null) {
                rules.add(compiled);
            }
        }

        if (rules.isEmpty()) {
            return null;
        }
        if (rules.size() == 1) {
            return rules.get(0);
        }
        return new CompositeRule(rules);
    }

    public IXTransformRule compileRule(XtRuleModel rule) {
        if (rule == null) {
            return null;
        }

        String xtType = rule.getXtType();
        if (xtType == null) {
            return compileRuleGroup((XtRuleGroupModel) rule);
        }

        switch (xtType) {
            case "apply-template":
                return compileApplyTemplate((XtApplyTemplateModel) rule);
            case "apply-mapping":
                return compileApplyMapping((XtApplyMappingModel) rule);
            case "copy-node":
                return compileCopyNode((XtCopyNodeModel) rule);
            case "copy-body":
                return compileCopyBody((XtCopyBodyModel) rule);
            case "value":
                return compileValue((XtValueModel) rule);
            case "gen":
                return compileGen((XtGenModel) rule);
            case "script":
                return compileScript((XtScriptModel) rule);
            case "each":
                return compileEach((XtEachModel) rule);
            case "xt:choose":
                return compileChoose((XtChooseModel) rule);
            case "xt:if":
                return compileIf((XtIfModel) rule);
            default:
                if (rule instanceof XtCustomTagModel) {
                    return compileCustomTag((XtCustomTagModel) rule);
                }
                return compileRuleGroup((XtRuleGroupModel) rule);
        }
    }

    private IXTransformRule compileApplyTemplate(XtApplyTemplateModel model) {
        IXSelector<XNode> xpath = model.getXpath();
        IXTransformRule bodyRule = compileRuleGroup(model);
        return new ApplyTemplateRule(model.getId(), xpath, model.isMandatory(), bodyRule);
    }

    private IXTransformRule compileApplyMapping(XtApplyMappingModel model) {
        IXSelector<XNode> xpath = model.getXpath();
        IXTransformRule bodyRule = compileRuleGroup(model);
        return new ApplyMappingRule(model.getId(), xpath, model.isMandatory(), bodyRule);
    }

    private IXTransformRule compileCopyNode(XtCopyNodeModel model) {
        return new CopyNodeRule(model.getXpath(), model.isMandatory());
    }

    private IXTransformRule compileCopyBody(XtCopyBodyModel model) {
        return new CopyBodyRule(model.getXpath(), model.isMandatory());
    }

    private IXTransformRule compileValue(XtValueModel model) {
        IEvalAction valueExpr = model.getBody();
        return new ValueRule(valueExpr, model.isMandatory());
    }

    private IXTransformRule compileGen(XtGenModel model) {
        IXSelector<XNode> xpath = model.getXpath();
        IXNodeGenerator generator = model.getBody();
        return new GenRule(xpath, generator);
    }

    private IXTransformRule compileScript(XtScriptModel model) {
        IXSelector<XNode> xpath = model.getXpath();
        IEvalAction script = model.getBody();
        return new ScriptRule(xpath, script);
    }

    private IXTransformRule compileEach(XtEachModel model) {
        IXSelector<XNode> xpath = model.getXpath();
        IXTransformRule bodyRule = compileRuleGroup(model);
        return new EachRule(xpath, bodyRule);
    }

    private IXTransformRule compileChoose(XtChooseModel model) {
        List<ChooseRule.WhenClause> whenClauses = new ArrayList<>();
        
        List<XtChooseWhenModel> whens = model.getWhens();
        if (whens != null) {
            for (XtChooseWhenModel when : whens) {
                IEvalAction condition = when.getTest();
                IXTransformRule rule = compileRuleGroup(when);
                whenClauses.add(new ChooseRule.WhenClause(condition, rule));
            }
        }

        IXTransformRule otherwiseRule = null;
        if (model.getOtherwise() != null) {
            otherwiseRule = compileRuleGroup(model.getOtherwise());
        }

        return new ChooseRule(whenClauses, otherwiseRule);
    }

    private IXTransformRule compileIf(XtIfModel model) {
        IEvalAction condition = model.getTest();
        IXTransformRule thenRule = compileRuleGroup(model);
        return new IfRule(condition, thenRule);
    }

    private IXTransformRule compileCustomTag(XtCustomTagModel model) {
        String tagName = model.getTagName();
        IXSelector<XNode> xpath = model.getXtXpath();
        Map<String, IEvalAction> attrs = model.getAttrs();
        IEvalAction xtAttrs = model.getXtAttrs();
        IXTransformRule bodyRule = compileRuleGroup(model);
        return new CustomTagRule(tagName, xpath, attrs, xtAttrs, bodyRule);
    }
}
