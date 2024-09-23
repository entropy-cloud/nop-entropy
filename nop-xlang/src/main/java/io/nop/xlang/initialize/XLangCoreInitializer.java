/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.initialize;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.global.EvalGlobalRegistry;
import io.nop.core.lang.json.bind.ValueResolverCompilerRegistry;
import io.nop.core.lang.xml.XPathProvider;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.bind.ExprValueResolver;
import io.nop.xlang.feature.FeatureConditionEvaluator;
import io.nop.xlang.functions.GlobalFunctions;
import io.nop.xlang.functions.LogFunctions;
import io.nop.xlang.janino.JaninoScriptCompiler;
import io.nop.xlang.utils.DebugHelper;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.impl.XDefToObjMeta;
import io.nop.xlang.xdef.parse.XDefinitionParser;
import io.nop.xlang.xdsl.json.DeltaExtendsGenerator;
import io.nop.xlang.xpath.DefaultXPathProvider;

public class XLangCoreInitializer implements ICoreInitializer {
    private Cancellable cleanup = new Cancellable();

    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_XLANG;
    }

    @Override
    public void initialize() {
        cleanup.append(EvalGlobalRegistry.instance().registerStaticFunctions(GlobalFunctions.class));
        cleanup.append(EvalGlobalRegistry.instance().registerStaticFunctions(DebugHelper.class));
        cleanup.append(EvalGlobalRegistry.instance().registerStaticFunctions(LogFunctions.class));

        XPathProvider.registerInstance(new DefaultXPathProvider());

        ValueResolverCompilerRegistry.DEFAULT.addResolverCompiler("expr", ExprValueResolver.COMPILER);
        cleanup.appendOnCancelTask(() -> {
            ValueResolverCompilerRegistry.DEFAULT.removeResolverCompiler("expr", ExprValueResolver.COMPILER);
        });

        EvalExprProvider.registerDefaultExprParser(this::parseExpr);
        EvalExprProvider.registerFeaturePredicateEvaluator(FeatureConditionEvaluator.INSTANCE);
        EvalExprProvider.registerDeltaExtendsGenerator(DeltaExtendsGenerator.INSTANCE);

        JaninoScriptCompiler.register();

//        registerXpl();
        registerXDef();
//        registerXMeta();
//        registerXlib();
//        registerXTask();

    }

    IEvalAction parseExpr(SourceLocation loc, String expr) {
        return XLang.newCompileTool().compileSimpleExpr(loc, expr);
    }

    //
//    private void registerXpl() {
//        ComponentModelConfig config = new ComponentModelConfig();
//        config.modelType(XLangConstants.MODEL_TYPE_XPL);
//        IResourceObjectLoader<IComponentModel> htmlLoader = new XplModelLoader(XLangOutputMode.html);
//        IResourceObjectLoader<IComponentModel> noneLoader = new XplModelLoader(XLangOutputMode.none);
//
//        config.loader(XLangConstants.FILE_TYPE_XPL, htmlLoader);
//        config.loader(XLangConstants.FILE_TYPE_XGEN, htmlLoader);
//        config.loader(XLangConstants.FILE_TYPE_XRUN, noneLoader);
//        cleanup.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
//    }
//
//    private void registerXMeta() {
//        ComponentModelConfig config = new ComponentModelConfig();
//        config.modelType(XLangConstants.MODEL_TYPE_XMETA);
//
//        config.loader(XLangConstants.MODEL_TYPE_XMETA,
//                path -> new DslModelParser(XDSL_SCHEMA_XMETA).parseFromVirtualPath(path));
//        config.loader(XLangConstants.MODEL_TYPE_XJAVA, path -> new JavaObjMetaParser().parseFromVirtualPath(path));
//
//        config.transformer(XLangConstants.MODEL_TYPE_XDEF, meta -> new ObjMetaToXDef().transform((IObjMeta) meta));
//
//        cleanup.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
//    }
//
    private void registerXDef() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(XLangConstants.MODEL_TYPE_XDEF);

        config.loader(XLangConstants.MODEL_TYPE_XDEF, path -> new XDefinitionParser().parseFromVirtualPath(path));

        config.transformer(XLangConstants.MODEL_TYPE_XMETA, def -> new XDefToObjMeta().transform((IXDefinition) def));

        cleanup.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }
//
//    private void registerXlib() {
//        ComponentModelConfig config = new ComponentModelConfig();
//        config.modelType(XLangConstants.MODEL_TYPE_XLIB);
//        config.loader(XLangConstants.FILE_TYPE_XLIB,
//                path -> new DslModelParser(XLangConstants.XDSL_SCHEMA_XLIB).parseFromVirtualPath(path));
//
//        cleanup.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
//    }
//
//    private void registerXTask() {
//        ComponentModelConfig config = new ComponentModelConfig();
//        config.modelType(XLangConstants.MODEL_TYPE_XTASK);
//        config.loader(XLangConstants.MODEL_TYPE_XTASK, new XplTaskLoader());
//
//        cleanup.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
//    }

    @Override
    public void destroy() {
        JaninoScriptCompiler.unregister();
        cleanup.cancel();
    }
}
