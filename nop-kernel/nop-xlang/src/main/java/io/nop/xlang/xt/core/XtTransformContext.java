/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.adapter.XNodeAdapter;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xt.IXTransformContext;
import io.nop.xlang.xt.IXTransformRule;
import io.nop.xlang.xt.model.XtTransformModel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_MAPPING_ID;
import static io.nop.xlang.XLangErrors.ERR_XT_MAPPING_NOT_FOUND;

public class XtTransformContext implements IXTransformContext {
    private final XtTransformModel transformModel;
    private final Map<String, IXTransformRule> templates;
    private final Map<String, Map<String, IXTransformRule>> mappings;
    private final Map<String, IXTransformRule> mappingDefaults;
    private final IEvalScope scope;
    private final Map<String, Object> parameters;
    private final IXtTransformOutput output;
    // 所有 child context 共享同一份循环引用检测状态
    private final Set<String> visitedTemplates;
    private final Set<String> visitedMappings;

    private XNode currentNode;
    private XNode rootNode;

    public XtTransformContext(XtTransformModel transformModel,
                              Map<String, IXTransformRule> templates,
                              Map<String, Map<String, IXTransformRule>> mappings,
                              Map<String, IXTransformRule> mappingDefaults,
                              Map<String, Object> parameters,
                              XNode outputRoot,
                              IEvalScope scope) {
        this(transformModel, templates, mappings, mappingDefaults, parameters,
                new XtTransformOutputImpl(outputRoot), scope, new HashSet<>(), new HashSet<>());
    }

    XtTransformContext(XtTransformModel transformModel,
                       Map<String, IXTransformRule> templates,
                       Map<String, Map<String, IXTransformRule>> mappings,
                       Map<String, IXTransformRule> mappingDefaults,
                       Map<String, Object> parameters,
                       IXtTransformOutput output,
                       IEvalScope scope,
                       Set<String> visitedTemplates,
                       Set<String> visitedMappings) {
        this.transformModel = transformModel;
        this.templates = templates;
        this.mappings = mappings;
        this.mappingDefaults = mappingDefaults;
        this.parameters = parameters != null ? parameters : new HashMap<>();
        this.scope = scope != null ? scope : XLang.newEvalScope();
        this.output = output;
        this.visitedTemplates = visitedTemplates;
        this.visitedMappings = visitedMappings;
        this.currentNode = null;
        this.rootNode = null;

        this.scope.setLocalValue(XtExprParser.VAR_NODE, null);
        this.scope.setLocalValue(XLangConstants.XPATH_VAR_THIS_NODE, null);
        this.scope.setLocalValue(XLangConstants.XPATH_VAR_ROOT, null);
        this.scope.setLocalValue(XtExprParser.VAR_OUTPUT, this.output);
        this.scope.setLocalValue(XtExprParser.VAR_PARAMS, this.parameters);
        this.scope.setLocalValue(XtExprParser.VAR_CONTEXT, this);
    }

    public XtTransformModel getTransformModel() {
        return transformModel;
    }

    public IXTransformRule getTemplate(String id) {
        return templates.get(id);
    }

    @Override
    public IXTransformRule getCompiledRuleForTag(String mappingId, String tagName) {
        Map<String, IXTransformRule> matches = mappings.get(mappingId);
        if (matches == null && !mappingDefaults.containsKey(mappingId))
            throw new NopException(ERR_XT_MAPPING_NOT_FOUND).param(ARG_MAPPING_ID, mappingId);

        if (matches != null) {
            IXTransformRule rule = matches.get(tagName);
            if (rule != null)
                return rule;
        }
        return mappingDefaults.get(mappingId);
    }

    @Deprecated
    @Override
    public IXTransformRule getRuleForTag(String mappingId, String tagName) {
        return getCompiledRuleForTag(mappingId, tagName);
    }

    public IXtTransformOutput getOutput() {
        return output;
    }

    public Object getVariable(String name) {
        return scope.getValue(name);
    }

    public void setVariable(String name, Object value) {
        scope.setLocalValue(name, value);
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    public Set<String> getVisitedTemplates() {
        return visitedTemplates;
    }

    @Override
    public Set<String> getVisitedMappings() {
        return visitedMappings;
    }

    public XtTransformContext childContext(XNode newNode) {
        XtTransformContext child = new XtTransformContext(transformModel, templates, mappings, mappingDefaults,
                parameters, output, scope, visitedTemplates, visitedMappings);
        child.currentNode = newNode;
        child.rootNode = this.rootNode != null ? this.rootNode : newNode;
        child.scope.setLocalValue(XtExprParser.VAR_NODE, newNode);
        child.scope.setLocalValue(XLangConstants.XPATH_VAR_THIS_NODE, newNode);
        child.scope.setLocalValue(XLangConstants.XPATH_VAR_ROOT, child.rootNode);
        return child;
    }

    /**
     * 切换当前节点并压入新的输出栈顶。调用方负责在适当的时候（推荐 try-finally）
     * 调用 {@code getOutput().popNode()} 还原输出栈。
     */
    public XtTransformContext childContext(XNode newNode, XNode newOutput) {
        XtTransformContext child = childContext(newNode);
        child.output.pushNode(newOutput);
        return child;
    }

    @Override
    public XNode root() {
        return rootNode;
    }

    @Override
    public XNode getThisNode() {
        return currentNode;
    }

    @Override
    public void setThisNode(XNode node) {
        setCurrentNode(node);
    }

    @Override
    public XNodeAdapter adapter() {
        return XNodeAdapter.INSTANCE;
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    public void setCurrentNode(XNode node) {
        this.currentNode = node;
        this.scope.setLocalValue(XtExprParser.VAR_NODE, node);
        this.scope.setLocalValue(XLangConstants.XPATH_VAR_THIS_NODE, node);
    }

    public void setRoot(XNode root) {
        this.rootNode = root;
        this.scope.setLocalValue(XLangConstants.XPATH_VAR_ROOT, root);
    }

    private static class XtTransformOutputImpl implements IXtTransformOutput {
        private final Deque<XNode> nodeStack = new ArrayDeque<>();

        public XtTransformOutputImpl(XNode rootNode) {
            nodeStack.push(rootNode);
        }

        @Override
        public void addChild(XNode node) {
            getCurrentNode().appendChild(node);
        }

        @Override
        public void setValue(Object value) {
            getCurrentNode().setContentValue(value);
        }

        @Override
        public void addAttr(String name, Object value) {
            getCurrentNode().setAttr(name, value);
        }

        @Override
        public XNode getCurrentNode() {
            return nodeStack.peek();
        }

        @Override
        public XNode newOutputNode(String tagName) {
            return XNode.make(tagName);
        }

        @Override
        public void pushNode(XNode node) {
            nodeStack.push(node);
        }

        @Override
        public void popNode() {
            if (nodeStack.size() > 1) {
                nodeStack.pop();
            }
        }
    }
}
