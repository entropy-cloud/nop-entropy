/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.core;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.adapter.XNodeAdapter;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xt.IXTransformContext;
import io.nop.xlang.xt.IXTransformRule;
import io.nop.xlang.xt.model.XtMappingMatchModel;
import io.nop.xlang.xt.model.XtMappingModel;
import io.nop.xlang.xt.model.XtTransformModel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class XtTransformContext implements IXTransformContext {
    private final XtTransformModel transformModel;
    private final Map<String, IXTransformRule> templates;
    private final Map<String, XtMappingModel> mappings;
    private final IEvalScope scope;
    private final Map<String, Object> parameters;
    private final IXtTransformOutput output;

    private XNode currentNode;
    private XNode rootNode;

    public XtTransformContext(XtTransformModel transformModel,
                              Map<String, IXTransformRule> templates,
                              Map<String, XtMappingModel> mappings,
                              Map<String, Object> parameters,
                              XNode outputRoot,
                              IEvalScope scope) {
        this.transformModel = transformModel;
        this.templates = templates;
        this.mappings = mappings;
        this.parameters = parameters != null ? parameters : new HashMap<>();
        this.scope = scope != null ? scope : XLang.newEvalScope();
        this.output = new XtTransformOutputImpl(outputRoot);
        this.currentNode = null;
        this.rootNode = null;
    }

    public XtTransformModel getTransformModel() {
        return transformModel;
    }

    public IXTransformRule getTemplate(String id) {
        return templates.get(id);
    }

    public XtMappingModel getMapping(String id) {
        return mappings.get(id);
    }

    public IXTransformRule getRuleForTag(String mappingId, String tagName) {
        XtMappingModel mapping = mappings.get(mappingId);
        if (mapping == null)
            return null;

        XtMappingMatchModel match = mapping.getMatch(tagName);
        if (match != null) {
            return (IXTransformRule) match;
        }
        return (IXTransformRule) mapping.getDefault();
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

    public XtTransformContext childContext(XNode newNode) {
        return childContext(newNode, output.getCurrentNode());
    }

    public XtTransformContext childContext(XNode newNode, XNode newOutput) {
        XtTransformContext child = new XtTransformContext(transformModel, templates, mappings, parameters, output.getCurrentNode(), scope);
        child.currentNode = newNode;
        child.rootNode = this.rootNode != null ? this.rootNode : newNode;
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
        this.currentNode = node;
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
    }

    public void setRoot(XNode root) {
        this.rootNode = root;
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
