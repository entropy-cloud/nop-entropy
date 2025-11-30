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
import io.nop.core.resource.IResourceObjectLoader;
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
import io.nop.xlang.xdef.parse.XDefinitionLoader;
import io.nop.xlang.xdsl.XDslConstants;
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

        registerXDef();
    }

    IEvalAction parseExpr(SourceLocation loc, String expr) {
        return XLang.newCompileTool().compileSimpleExpr(loc, expr);
    }

    private void registerXDef() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(XLangConstants.MODEL_TYPE_XDEF);

        config.loader(XLangConstants.MODEL_TYPE_XDEF, new ComponentModelConfig.LoaderConfig(
                "xdsl-loader", null, XDslConstants.XDSL_SCHEMA_XDEF,
                null, (IResourceObjectLoader) new XDefinitionLoader()));

        config.transformer(XLangConstants.MODEL_TYPE_XMETA, def -> new XDefToObjMeta().transform((IXDefinition) def));

        cleanup.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }

    @Override
    public void destroy() {
        JaninoScriptCompiler.unregister();
        cleanup.cancel();
    }
}
