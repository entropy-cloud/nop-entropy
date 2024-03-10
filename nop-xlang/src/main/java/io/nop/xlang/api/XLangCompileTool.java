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
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.functions.NoopEvalFunction;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.type.IFunctionType;
import io.nop.core.type.IGenericType;
import io.nop.xlang.ast.ArrowFunctionExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.ParameterDeclaration;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.ast.XLangTypeHelper;
import io.nop.xlang.exec.FunctionExecutable;
import io.nop.xlang.exec.GenXJsonExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.expr.ExprConstants;
import io.nop.xlang.expr.ExprPhase;
import io.nop.xlang.xpl.IXplCompiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.nop.core.type.PredefinedGenericTypes.X_NODE_TYPE;
import static io.nop.xlang.XLangErrors.ARG_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_NOT_LITERAL_VALUE;
import static io.nop.xlang.ast.definition.ScopeVarDefinition.readOnly;

public class XLangCompileTool {

    private final IXplCompiler cp;
    private final IXLangCompileScope scope;
    private boolean optimize = true;
    private String configText;

    public XLangCompileTool(IXplCompiler cp) {
        this.cp = cp;
        this.scope = cp.newCompileScope();
        this.scope.registerScopeVarDefinition(readOnly(ExprConstants.SCOPE_VAR_XPL_NODE, X_NODE_TYPE), true);
    }

    public XLangCompileTool(IXLangCompileScope scope) {
        this.cp = scope.getCompiler();
        this.scope = scope;
    }

    public String getConfigText() {
        return configText;
    }

    public void setConfigText(String configText) {
        this.configText = configText;
    }

    public XLangCompileTool allowUnregisteredScopeVar(boolean b) {
        scope.setAllowUnregisteredScopeVar(b);
        return this;
    }

    public boolean isAllowUnregisteredScopeVar() {
        return scope.isAllowUnregisteredScopeVar();
    }

    public XLangCompileTool disableOptimize() {
        this.optimize = false;
        return this;
    }

    public XLangCompileTool outputMode(XLangOutputMode outputMode) {
        scope.setOutputMode(outputMode);
        return this;
    }

    public XLangOutputMode getOutputMode() {
        return scope.getOutputMode();
    }

    public XLangCompileTool enableOptimize() {
        this.optimize = true;
        return this;
    }

    public IXplCompiler getCompiler() {
        return cp;
    }

    public IXLangCompileScope getScope() {
        return scope;
    }

    public XLangCompileTool loadLib(SourceLocation loc, String namespace, String lib) {
        getCompiler().loadLib(loc, namespace, lib, getScope());
        return this;
    }

    public ExprEvalAction compileScript(SourceLocation loc, String lang, String script) {
        IEvalFunction func = cp.compileScript(loc, lang, script, scope);
        IExecutableExpression executable = FunctionExecutable.build(loc, "<script>", func,
                IExecutableExpression.EMPTY_EXPRS);
        return buildAction(executable);
    }

    public Program parseFullExpr(SourceLocation loc, String source) {
        return cp.parseFullExpr(loc, source, scope);
    }

    public ExprEvalAction compileFullExpr(SourceLocation loc, String source) {
        Expression expr = cp.parseFullExpr(loc, source, scope);
        return buildEvalAction(expr);
    }

    public ExprEvalAction compileSimpleExpr(SourceLocation loc, String source) {
        Expression expr = cp.parseSimpleExpr(loc, source, scope);
        return buildEvalAction(expr);
    }

    public Object getStaticValue(SourceLocation loc, String source) {
        Expression expr = cp.parseTemplateExpr(loc, source, false, ExprPhase.eval, scope);
        if (expr == null)
            return null;

        if (!(expr instanceof Literal))
            throw new NopException(ERR_EXEC_NOT_LITERAL_VALUE).loc(loc).param(ARG_EXPR, source);
        return ((Literal) expr).getValue();
    }

    public ExprEvalAction compileTemplateExpr(SourceLocation loc, String source, boolean singleExpr, ExprPhase phase) {
        Expression expr = cp.parseTemplateExpr(loc, source, singleExpr, phase, scope);
        return buildEvalAction(expr);
    }

    public ExprEvalAction compileTag(XNode node) {
        ValueWithLocation vl = scope.recordValueLocation(ExprConstants.SCOPE_VAR_XPL_NODE);
        scope.setLocalValue(null, ExprConstants.SCOPE_VAR_XPL_NODE, node);
        try {
            Expression expr = cp.parseTag(node, scope);
            // 确保存在顶层变量作用域
            expr = toProgram(expr);
            return buildEvalAction(expr);
        } finally {
            scope.restoreValueLocation(ExprConstants.SCOPE_VAR_XPL_NODE, vl);
        }
    }

    public ExprEvalAction compileTag(XNode node, XLangOutputMode outputMode) {
        XLangOutputMode oldMode = scope.getOutputMode();
        scope.setOutputMode(outputMode);
        try {
            return compileTag(node);
        } finally {
            scope.setOutputMode(oldMode);
        }
    }

    public ExprEvalAction compileXjson(XNode node) {
        ValueWithLocation vl = scope.recordValueLocation(ExprConstants.SCOPE_VAR_XPL_NODE);
        scope.setLocalValue(null, ExprConstants.SCOPE_VAR_XPL_NODE, node);
        XLangOutputMode oldMode = scope.getOutputMode();
        try {
            scope.setOutputMode(XLangOutputMode.xjson);
            Expression expr = node.hasChild() ? cp.parseTagBody(node, scope)
                    : cp.parseFullExpr(node.content().getLocation(), node.contentText(), scope);

            expr = toProgram(expr);
            if (expr == null)
                return null;
            IExecutableExpression executable = cp.buildExecutable(expr, true, scope);
            return new ExprEvalAction(new GenXJsonExecutable(executable));
        } finally {
            scope.setOutputMode(oldMode);
            scope.restoreValueLocation(ExprConstants.SCOPE_VAR_XPL_NODE, vl);
        }
    }

    public ExprEvalAction compileTagBody(XNode node) {
        Expression expr = parseTagBody(node, scope.getOutputMode());
        return buildEvalAction(expr);
    }

    public Expression parseTagBody(XNode node, XLangOutputMode outputMode) {
        XLangOutputMode oldMode = scope.getOutputMode();
        ValueWithLocation vl = scope.recordValueLocation(ExprConstants.SCOPE_VAR_XPL_NODE);
        scope.setLocalValue(null, ExprConstants.SCOPE_VAR_XPL_NODE, node);
        scope.setOutputMode(outputMode);
        try {
            if (!node.hasChild()) {
                if (node.hasContent()) {
                    // 如果不允许输出，则只有文本内容时认为是XLang脚本
                    if (scope.getOutputMode() == XLangOutputMode.none) {
                        ValueWithLocation content = node.content();
                        return cp.parseFullExpr(content.getLocation(), content.asString(), scope);
                    }
                }
            }
            Expression expr = cp.parseTagBody(node, scope);
            expr = toProgram(expr);
            return expr;
        } finally {
            scope.restoreValueLocation(ExprConstants.SCOPE_VAR_XPL_NODE, vl);
            scope.setOutputMode(oldMode);
        }
    }

    public ExprEvalAction compileTagBody(XNode node, XLangOutputMode outputMode) {
        ValueWithLocation vl = scope.recordValueLocation(ExprConstants.SCOPE_VAR_XPL_NODE);
        scope.setLocalValue(null, ExprConstants.SCOPE_VAR_XPL_NODE, node);
        XLangOutputMode oldMode = scope.getOutputMode();
        scope.setOutputMode(outputMode);
        try {
            return compileTagBody(node);
        } finally {
            scope.setOutputMode(oldMode);
            scope.restoreValueLocation(ExprConstants.SCOPE_VAR_XPL_NODE, vl);
        }
    }

    public EvalCode compileTagBodyWithSource(XNode node, XLangOutputMode outputMode) {
        ExprEvalAction action = compileTagBody(node, outputMode);
        if (action == null)
            return null;
        return new EvalCode(action, node.xml());
    }

    private Program toProgram(Expression expr) {
        if (expr == null)
            return null;
        if (expr instanceof Program)
            return (Program) expr;
        return Program.script(expr.getLocation(), Collections.singletonList(expr));
    }

    public IEvalFunction compileFunction(ArrowFunctionExpression lambda, String source) {
        Expression expr = cp.parseFullExpr(lambda.getLocation(), source, scope);
        if (expr == null)
            return NoopEvalFunction.INSTANCE;

        lambda.setBody(expr);

        return compileFunction(lambda);
    }

    public IEvalFunction compileFunction(ArrowFunctionExpression lambda) {
        IExecutableExpression executable = cp.buildExecutable(lambda, optimize, scope);
        if (executable == null)
            return NoopEvalFunction.INSTANCE;

        IEvalFunction invoker = buildInvoker(executable);
        return invoker;
    }

    private IEvalFunction buildInvoker(IExecutableExpression executable) {
        return (IEvalFunction) ((LiteralExecutable) executable).getValue();
    }

    public ExprEvalAction compileXpl(SourceLocation loc, String source) {
        XNode node = XNodeParser.instance().parseFromText(loc, source);
        return compileTag(node);
    }

    public ExprEvalAction buildEvalAction(Expression expr) {
        if (expr == null) {
            return null;
        }
        return buildAction(cp.buildExecutable(expr, optimize, scope));
    }


    public IExecutableExpression buildExecutable(Expression expr) {
        return cp.buildExecutable(expr, optimize, scope);
    }

    private ExprEvalAction buildAction(IExecutableExpression expr) {
        if (expr == null)
            return null;
        return new ExprEvalAction(expr);
    }

    public IEvalFunction compileEvalFunction(XNode node, IFunctionType functionType, XLangOutputMode outputMode) {
        Expression body = parseTagBody(node, outputMode);

        ArrowFunctionExpression func = new ArrowFunctionExpression();
        func.setLocation(node.getLocation());
        func.setFuncName("<" + node.getTagName() + ">");
        List<ParameterDeclaration> params = new ArrayList<>(functionType.getFuncArgCount());
        List<String> names = functionType.getFuncArgNames();
        List<IGenericType> types = functionType.getFuncArgTypes();
        for (int i = 0, n = names.size(); i < n; i++) {
            ParameterDeclaration param = new ParameterDeclaration();
            param.setType(XLangTypeHelper.buildTypeNode(types.get(i)));
            param.setName(XLangASTBuilder.identifier(null, names.get(i)));
        }
        func.setParams(params);
        func.setReturnType(XLangTypeHelper.buildTypeNode(functionType.getFuncReturnType()));
        func.setBody(body);

        return compileFunction(func);
    }
}