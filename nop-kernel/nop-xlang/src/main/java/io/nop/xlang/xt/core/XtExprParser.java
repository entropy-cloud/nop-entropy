/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.core;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.ast.CustomExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.exec.AbstractExecutable;
import io.nop.xlang.expr.ExprFeatures;
import io.nop.xlang.expr.ExprPhase;
import io.nop.xlang.xpath.parse.XPathExprParser;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * xt 表达式解析器。在 XPathExprParser 的 {@code @attrName} 简写基础上，
 * 额外支持 xt 内置变量 {@code $node/$thisNode/$root/$output/$params/$context}。
 * 这些 {@code $} 前缀名在 XLang 平台中保留给 EvalGlobalRegistry 全局变量，
 * 因此在 xt 表达式语境下通过本解析器显式拦截并映射到 scope 局部变量
 * （不带 {@code $} 前缀的同名变量，由 XtTransformContext 统一注入）。
 */
public class XtExprParser extends XPathExprParser {
    public static final String VAR_NODE = "node";
    public static final String VAR_OUTPUT = "output";
    public static final String VAR_PARAMS = "params";
    public static final String VAR_CONTEXT = "context";

    public static final Set<String> BUILTIN_SCOPE_VARS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            VAR_NODE, VAR_OUTPUT, VAR_PARAMS, VAR_CONTEXT,
            XLangConstants.XPATH_VAR_THIS_NODE, XLangConstants.XPATH_VAR_ROOT)));

    private static final Set<String> BUILTIN_VARS = BUILTIN_SCOPE_VARS;

    public XtExprParser() {
        // 与 XplExprParser 对齐：启用全部表达式特性（成员访问、方法调用等）
        setUseEvalException(true);
        enableFeatures(ExprFeatures.ALL);
    }

    /**
     * xt 表达式同时接受 <code>${...}</code> 与历史遗留的 <code>%{...}</code> 定界符，
     * 因此以 transform 阶段调用时额外识别 <code>${</code> 起始。
     */
    @Override
    public boolean isExprStart(TextScanner sc, ExprPhase phase) {
        if (phase == ExprPhase.transform && sc.cur == '$' && sc.peek() == '{')
            return true;
        return super.isExprStart(sc, phase);
    }

    /**
     * {@code $name} 形式的内置变量在 primaryExpr 入口拦截：{@code $} 是合法的
     * Java 标识符起始字符，若不在此拦截会被标准解析器当作 Identifier 交给
     * LexicalScopeAnalysis 的全局变量分支（EvalGlobalRegistry）而报错。
     */
    @Override
    protected Expression primaryExpr(TextScanner sc) {
        if (sc.cur == '$' && StringHelper.isJavaIdentifierStart(sc.peek())) {
            SourceLocation loc = sc.location();
            int pos = sc.pos;
            int cur = sc.cur;
            int line = sc.line;
            int col = sc.col;
            String name = sc.nextJavaVar();
            String varName = name.substring(1);
            if (BUILTIN_VARS.contains(varName)) {
                sc.skipBlank();
                return scopeVarExpression(loc, name, varName);
            }
            // 非内置变量，恢复扫描位置，交由标准解析逻辑处理（全局变量等）
            sc.pos = pos;
            sc.cur = cur;
            sc.line = line;
            sc.col = col;
        }
        return super.primaryExpr(sc);
    }

    private Expression scopeVarExpression(SourceLocation loc, String displayName, String varName) {
        CustomExpression ret = new CustomExpression();
        ret.setLocation(loc);
        ret.setSource(displayName);
        ret.setExecutable(new AbstractExecutable(loc) {
            @Override
            public void display(StringBuilder sb) {
                sb.append(displayName);
            }

            @Override
            public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
                return rt.getScope().getValue(varName);
            }
        });
        return ret;
    }
}
