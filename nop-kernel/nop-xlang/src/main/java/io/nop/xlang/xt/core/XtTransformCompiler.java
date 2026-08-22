/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xt.IXTransformRule;
import io.nop.xlang.xt.loader.XtTransformModelLoader;
import io.nop.xlang.xt.model.XtApplyMappingModel;
import io.nop.xlang.xt.model.XtApplyTemplateModel;
import io.nop.xlang.xt.model.XtChooseModel;
import io.nop.xlang.xt.model.XtChooseWhenModel;
import io.nop.xlang.xt.model.XtCopyBodyModel;
import io.nop.xlang.xt.model.XtCopyNodeModel;
import io.nop.xlang.xt.model.XtCustomTagModel;
import io.nop.xlang.xt.model.XtEachModel;
import io.nop.xlang.xt.model.XtGenModel;
import io.nop.xlang.xt.model.XtIfModel;
import io.nop.xlang.xt.model.XtImportModel;
import io.nop.xlang.xt.model.XtMappingMatchModel;
import io.nop.xlang.xt.model.XtMappingModel;
import io.nop.xlang.xt.model.XtRuleGroupModel;
import io.nop.xlang.xt.model.XtRuleModel;
import io.nop.xlang.xt.model.XtScriptModel;
import io.nop.xlang.xt.model.XtTemplateModel;
import io.nop.xlang.xt.model.XtTransformModel;
import io.nop.xlang.xt.model.XtValueModel;
import io.nop.xlang.xt.rules.ApplyMappingRule;
import io.nop.xlang.xt.rules.ApplyTemplateRule;
import io.nop.xlang.xt.rules.ChooseRule;
import io.nop.xlang.xt.rules.CompositeRule;
import io.nop.xlang.xt.rules.CopyBodyRule;
import io.nop.xlang.xt.rules.CopyNodeRule;
import io.nop.xlang.xt.rules.CustomTagRule;
import io.nop.xlang.xt.rules.EachRule;
import io.nop.xlang.xt.rules.GenRule;
import io.nop.xlang.xt.rules.IfRule;
import io.nop.xlang.xt.rules.ScriptRule;
import io.nop.xlang.xt.rules.ValueOutputRule;
import io.nop.xlang.xt.rules.ValueRule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_ID;
import static io.nop.xlang.XLangErrors.ARG_MAPPING_ID;
import static io.nop.xlang.XLangErrors.ARG_PATH;
import static io.nop.xlang.XLangErrors.ARG_RULE_TYPE;
import static io.nop.xlang.XLangErrors.ARG_TEMPLATE_ID;
import static io.nop.xlang.XLangErrors.ERR_XT_CIRCULAR_REFERENCE;
import static io.nop.xlang.XLangErrors.ERR_XT_IMPORT_CONFLICT;
import static io.nop.xlang.XLangErrors.ERR_XT_MAPPING_NOT_FOUND;
import static io.nop.xlang.XLangErrors.ERR_XT_RULE_COMPILE_ERROR;
import static io.nop.xlang.XLangErrors.ERR_XT_TEMPLATE_NOT_FOUND;

public class XtTransformCompiler {

    private IXTransformRule mainRule;
    private Map<String, IXTransformRule> templates = new LinkedHashMap<>();
    private Map<String, Map<String, IXTransformRule>> mappings = new LinkedHashMap<>();
    private Map<String, IXTransformRule> mappingDefaults = new LinkedHashMap<>();
    private final List<RuleRef> pendingRefs = new ArrayList<>();
    private final Set<String> visitedImportPaths = new HashSet<>();
    private final Deque<String> resolvingMappings = new ArrayDeque<>();

    static class RuleRef {
        final String id;
        final SourceLocation loc;
        final boolean template;

        RuleRef(String id, SourceLocation loc, boolean template) {
            this.id = id;
            this.loc = loc;
            this.template = template;
        }
    }

    public void compileAll(XtTransformModel model) {
        compileAll(model, new HashSet<>());
    }

    private void compileAll(XtTransformModel model, Set<String> importStack) {
        compileImports(model, importStack);
        compileTemplates(model);
        compileMappings(model);
        this.mainRule = compileRuleGroup(model.getMain());
        validateReferences();
    }

    /**
     * 仅编译 main 规则组。完整编译（imports/templates/mappings/ID 检查）走 {@link #compileAll}。
     */
    public IXTransformRule compile(XtTransformModel model) {
        return compileRuleGroup(model.getMain());
    }

    /**
     * 编译当前模型自身的 templates。已合并 imports 之后调用，
     * 与 import 登记的 id 冲突时抛出 ERR_XT_IMPORT_CONFLICT。
     */
    public Map<String, IXTransformRule> compileTemplates(XtTransformModel model) {
        if (model.getTemplates() != null) {
            for (XtTemplateModel template : model.getTemplates()) {
                registerTemplate(template.getId(), compileRuleGroup(template), template.getLocation());
            }
        }
        return templates;
    }

    /**
     * 编译当前模型自身的 mappings（含 inherits 合并），产出
     * mappingId -&gt; (tagName -&gt; rule) 与 mappingId -&gt; default rule 两个 cache。
     */
    public Map<String, Map<String, IXTransformRule>> compileMappings(XtTransformModel model) {
        if (model.getMappings() != null) {
            Map<String, XtMappingModel> ownMappings = new LinkedHashMap<>();
            for (XtMappingModel mapping : model.getMappings()) {
                ownMappings.put(mapping.getId(), mapping);
            }
            for (XtMappingModel mapping : model.getMappings()) {
                compileMapping(mapping, ownMappings, true);
            }
        }
        return mappings;
    }

    /**
     * @param own true 表示当前文件自身的 mapping 定义，与 import 登记的 id 冲突时抛错；
     *            false 表示 inherits 递归解析，已编译过的 mapping 直接复用
     */
    private void compileMapping(XtMappingModel mapping, Map<String, XtMappingModel> ownMappings, boolean own) {
        String id = mapping.getId();
        if (mappings.containsKey(id)) {
            if (own)
                throw importConflict(id, mapping.getLocation());
            return;
        }

        if (resolvingMappings.contains(id))
            throw circularReference(id);

        resolvingMappings.push(id);
        try {
            Map<String, IXTransformRule> merged = new LinkedHashMap<>();
            IXTransformRule defaultRule = null;

            Set<String> inherits = mapping.getInherits();
            if (inherits != null) {
                // 多重继承按 csv 顺序后者覆盖前者
                for (String parentId : inherits) {
                    XtMappingModel parent = ownMappings.get(parentId);
                    if (parent == null)
                        throw mappingNotFound(parentId, mapping.getLocation());

                    compileMapping(parent, ownMappings, false);
                    Map<String, IXTransformRule> parentRules = mappings.get(parentId);
                    merged.putAll(parentRules);
                    IXTransformRule parentDefault = mappingDefaults.get(parentId);
                    if (parentDefault != null)
                        defaultRule = parentDefault;
                }
            }

            // 子 mapping 的同名 match 覆盖父 mapping
            if (mapping.getMatchs() != null) {
                for (XtMappingMatchModel match : mapping.getMatchs()) {
                    merged.put(match.getTag(), compileRuleGroup(match));
                }
            }
            // 子无 default 时继承父 default
            if (mapping.getDefault() != null)
                defaultRule = compileRuleGroup(mapping.getDefault());

            mappings.put(id, merged);
            mappingDefaults.put(id, defaultRule);
        } finally {
            resolvingMappings.pop();
        }
    }

    private void compileImports(XtTransformModel model, Set<String> importStack) {
        if (!model.hasImports())
            return;

        for (XtImportModel imp : model.getImports()) {
            String from = imp.getFrom();
            if (StringHelper.isEmpty(from))
                continue;

            if (!visitedImportPaths.add(from))
                throw importConflict(from, imp.getLocation());

            if (!importStack.add(from))
                throw circularReference(from);

            try {
                XtTransformModel importedModel = new XtTransformModelLoader().loadObjectFromPath(from);
                XtTransformCompiler childCompiler = new XtTransformCompiler();
                childCompiler.compileAll(importedModel, importStack);

                String prefix = imp.getPrefix();
                for (Map.Entry<String, IXTransformRule> entry : childCompiler.templates.entrySet()) {
                    registerTemplate(prefixedId(prefix, entry.getKey()), entry.getValue(), imp.getLocation());
                }
                for (Map.Entry<String, Map<String, IXTransformRule>> entry : childCompiler.mappings.entrySet()) {
                    String key = prefixedId(prefix, entry.getKey());
                    if (mappings.containsKey(key))
                        throw importConflict(key, imp.getLocation());
                    mappings.put(key, entry.getValue());
                    mappingDefaults.put(key, childCompiler.mappingDefaults.get(entry.getKey()));
                }
            } finally {
                importStack.remove(from);
            }
        }
    }

    private static String prefixedId(String prefix, String id) {
        return StringHelper.isEmpty(prefix) ? id : prefix + ":" + id;
    }

    private void registerTemplate(String id, IXTransformRule rule, SourceLocation loc) {
        if (templates.containsKey(id))
            throw importConflict(id, loc);
        templates.put(id, rule);
    }

    /**
     * 编译期 ID 存在性检查：所有 apply-template / apply-mapping 引用的 id
     * 必须在已合并 imports / inherits 之后的 cache 中存在。
     */
    private void validateReferences() {
        for (RuleRef ref : pendingRefs) {
            if (ref.template) {
                if (!templates.containsKey(ref.id))
                    throw templateNotFound(ref.id, ref.loc);
            } else {
                if (!mappings.containsKey(ref.id))
                    throw mappingNotFound(ref.id, ref.loc);
            }
        }
    }

    public IXTransformRule getMainRule() {
        return mainRule;
    }

    public Map<String, IXTransformRule> getTemplates() {
        return templates;
    }

    public Map<String, Map<String, IXTransformRule>> getCompiledMappings() {
        return mappings;
    }

    public Map<String, IXTransformRule> getMappingDefaults() {
        return mappingDefaults;
    }

    public IXTransformRule compileRuleGroup(XtRuleGroupModel group) {
        if (group == null)
            return null;

        List<IXTransformRule> rules = new ArrayList<>();

        if (group.getValue() != null) {
            rules.add(new ValueOutputRule(group.getValue()));
        }

        if (group.getBody() != null && !group.getBody().isEmpty()) {
            for (XtRuleModel rule : group.getBody()) {
                IXTransformRule compiled = compileRule(rule);
                if (compiled != null) {
                    rules.add(compiled);
                }
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
        try {
            if (xtType == null) {
                return compileRuleGroup((XtRuleGroupModel) rule);
            }

            IXTransformRule compiled = compileKnownRule(xtType, rule);
            if (compiled != null)
                return compiled;

            if (rule instanceof XtCustomTagModel) {
                return compileCustomTag((XtCustomTagModel) rule);
            }
            return compileRuleGroup((XtRuleGroupModel) rule);
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new NopException(ERR_XT_RULE_COMPILE_ERROR)
                    .param(ARG_RULE_TYPE, xtType)
                    .loc(rule.getLocation())
                    .cause(e);
        }
    }

    /**
     * @return null 表示 xtType 不是已知指令，由调用方走 custom tag / rule group 兜底
     */
    private IXTransformRule compileKnownRule(String xtType, XtRuleModel rule) {
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
                return null;
        }
    }

    private IXTransformRule compileApplyTemplate(XtApplyTemplateModel model) {
        IXSelector<XNode> xpath = model.getXpath();
        IXTransformRule bodyRule = compileRuleGroup(model);
        pendingRefs.add(new RuleRef(model.getId(), model.getLocation(), true));
        return new ApplyTemplateRule(model.getId(), xpath, model.isMandatory(), bodyRule);
    }

    private IXTransformRule compileApplyMapping(XtApplyMappingModel model) {
        IXSelector<XNode> xpath = model.getXpath();
        IXTransformRule bodyRule = compileRuleGroup(model);
        pendingRefs.add(new RuleRef(model.getId(), model.getLocation(), false));
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

    private static NopException importConflict(String id, SourceLocation loc) {
        return new NopException(ERR_XT_IMPORT_CONFLICT).param(ARG_ID, id).loc(loc);
    }

    private static NopException circularReference(String path) {
        return new NopException(ERR_XT_CIRCULAR_REFERENCE).param(ARG_PATH, path);
    }

    private static NopException templateNotFound(String id, SourceLocation loc) {
        return new NopException(ERR_XT_TEMPLATE_NOT_FOUND).param(ARG_TEMPLATE_ID, id).loc(loc);
    }

    private static NopException mappingNotFound(String id, SourceLocation loc) {
        return new NopException(ERR_XT_MAPPING_NOT_FOUND).param(ARG_MAPPING_ID, id).loc(loc);
    }
}
