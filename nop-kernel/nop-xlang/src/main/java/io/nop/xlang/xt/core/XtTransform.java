/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.core;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xt.IXTransformRule;
import io.nop.xlang.xt.model.XtMappingModel;
import io.nop.xlang.xt.model.XtTransformModel;

import java.util.Collections;
import java.util.Map;

public class XtTransform {
    private final XtTransformModel model;
    private final IXTransformRule mainRule;
    private final Map<String, IXTransformRule> templates;
    private final Map<String, XtMappingModel> mappings;

    public XtTransform(XtTransformModel model) {
        this.model = model;
        XtTransformCompiler compiler = new XtTransformCompiler();
        this.mainRule = compiler.compile(model);
        this.templates = compiler.compileTemplates(model);
        this.mappings = compiler.getMappings(model);
    }

    public XNode transform(XNode source) {
        return transform(source, Collections.emptyMap());
    }

    public XNode transform(XNode source, Map<String, Object> params) {
        XNode outputRoot = XNode.make("root");
        XtTransformContext context = new XtTransformContext(model, templates, mappings, params, outputRoot, XLang.newEvalScope());
        context.setCurrentNode(source);
        context.setRoot(source);

        if (mainRule != null) {
            mainRule.apply(outputRoot, source, context);
        }

        if (outputRoot.getChildCount() == 1) {
            return outputRoot.child(0);
        }
        return outputRoot;
    }

    public static XtTransform load(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return load(resource);
    }

    public static XtTransform load(IResource resource) {
        XtTransformModel model = (XtTransformModel) new DslModelParser("/nop/schema/xt.xdef").parseFromResource(resource);
        return new XtTransform(model);
    }

    public XtTransformModel getModel() {
        return model;
    }

    public IXTransformRule getMainRule() {
        return mainRule;
    }

    public Map<String, IXTransformRule> getTemplates() {
        return templates;
    }

    public Map<String, XtMappingModel> getMappings() {
        return mappings;
    }
}
