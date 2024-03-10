/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.api;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.ast.definition.ScopeVarDefinition;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.impl.DefaultXLangProvider;
import io.nop.xlang.xpl.impl.XplModelParser;

import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_LIB_PATH;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ERR_XLIB_UNKNOWN_TAG;

public class XLang {
    private static IXLangProvider _provider = new DefaultXLangProvider();

    public static boolean isGlobalVarName(String varName) {
        return varName.charAt(0) == '$' || varName.equals("_");
    }

    public static Object execute(IExecutableExpression expr, IEvalScope scope) {
        return scope.getExpressionExecutor().execute(expr, scope);
    }

    public static IEvalScope newEvalScope(Map<String, Object> context) {
        return EvalExprProvider.newEvalScope(context);
    }

    public static IEvalScope newEvalScope() {
        return EvalExprProvider.newEvalScope();
    }

    public static IXplCompiler newXplCompiler() {
        return _provider.newXplCompiler();
    }

    public static XLangCompileTool newCompileTool() {
        return new XLangCompileTool(newXplCompiler());
    }

    public static XplModel parseXpl(IResource resource, XLangOutputMode outputMode) {
        return new XplModelParser().outputModel(outputMode).parseFromResource(resource);
    }

    public static XplModel parseXpl(IResource resource) {
        return parseXpl(resource, XLangOutputMode.html);
    }

    public static XplModel loadTpl(String path) {
        return (XplModel) ResourceComponentManager.instance().loadComponentModel(path);
    }

    public static AbstractEvalAction getTagAction(String libPath, String tagName) {
        IXplTagLib lib = (IXplTagLib) ResourceComponentManager.instance().loadComponentModel(libPath);
        IXplTag tag = lib.getTag(tagName);
        if (tag == null)
            throw new NopException(ERR_XLIB_UNKNOWN_TAG).param(ARG_LIB_PATH, libPath).param(ARG_TAG_NAME, tagName);

        IFunctionModel func = tag.getFunctionModel();
        return new FunctionEvalAction(func);
    }

    public static IXplTag getTag(String libPath, String tagName) {
        IXplTagLib lib = (IXplTagLib) ResourceComponentManager.instance().loadComponentModel(libPath);
        IXplTag tag = lib.getTag(tagName);
        if (tag == null)
            throw new NopException(ERR_XLIB_UNKNOWN_TAG).param(ARG_LIB_PATH, libPath).param(ARG_TAG_NAME, tagName);
        return tag;
    }

    public static Object genJsonExtends(SourceLocation loc, String source, Map<String, Object> json) {
        XLangCompileTool tool = newCompileTool();
        tool.getScope().registerScopeVarDefinition(
                ScopeVarDefinition.readOnly(XLangConstants.VAR_JSON, PredefinedGenericTypes.MAP_STRING_ANY_TYPE),
                false);

        XNode node = XNodeParser.instance().forFragments(true).parseFromText(loc, source);
        ExprEvalAction action = tool.compileTagBody(node);
        IEvalScope scope = newEvalScope();
        scope.setLocalValue(null, XLangConstants.VAR_JSON, json);
        return action.invoke(scope);
    }
}
