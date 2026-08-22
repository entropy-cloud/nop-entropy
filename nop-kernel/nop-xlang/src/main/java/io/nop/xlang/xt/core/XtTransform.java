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
import io.nop.xlang.xt.IXTransform;
import io.nop.xlang.xt.IXTransformRule;
import io.nop.xlang.xt.model.XtTransformModel;

import java.util.Collections;
import java.util.Map;

public class XtTransform implements IXTransform {
    private final XtTransformModel model;
    private final IXTransformRule mainRule;
    private final Map<String, IXTransformRule> templates;
    private final Map<String, Map<String, IXTransformRule>> mappings;
    private final Map<String, IXTransformRule> mappingDefaults;

    public XtTransform(XtTransformModel model) {
        this.model = model;
        XtTransformCompiler compiler = new XtTransformCompiler();
        compiler.compileAll(model);
        this.mainRule = compiler.getMainRule();
        this.templates = compiler.getTemplates();
        this.mappings = compiler.getCompiledMappings();
        this.mappingDefaults = compiler.getMappingDefaults();
    }

    @Override
    public XNode transform(XNode source) {
        return transform(source, Collections.emptyMap());
    }

    @Override
    public XNode transform(XNode source, Map<String, Object> params) {
        XNode outputRoot = XNode.make("root");
        XtTransformContext context = new XtTransformContext(model, templates, mappings, mappingDefaults,
                params, outputRoot, XLang.newEvalScope());
        context.setCurrentNode(source);
        context.setRoot(source);
        bindParameters(context);

        if (mainRule != null) {
            mainRule.apply(outputRoot, source, context);
        }

        if (outputRoot.getChildCount() == 1) {
            return outputRoot.child(0);
        }
        return outputRoot;
    }

    /**
     * 把 params 的每个 key 同时暴露为 scope 局部变量，便于规则/XPL 体内直接以 ${name} 引用。
     * 与内置变量 node/thisNode/root/output/params/context 同名时以内置变量优先，不覆盖。
     */
    private void bindParameters(XtTransformContext context) {
        Map<String, Object> params = context.getParameters();
        if (params == null || params.isEmpty())
            return;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String name = entry.getKey();
            if (name == null || XtExprParser.BUILTIN_SCOPE_VARS.contains(name))
                continue;
            context.setVariable(name, entry.getValue());
        }
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

    /**
     * mappingId -&gt; (tagName -&gt; compiled rule)，已合并 imports（prefix 前缀 key）与 inherits。
     */
    public Map<String, Map<String, IXTransformRule>> getMappings() {
        return mappings;
    }

    public Map<String, IXTransformRule> getMappingDefaults() {
        return mappingDefaults;
    }
}
