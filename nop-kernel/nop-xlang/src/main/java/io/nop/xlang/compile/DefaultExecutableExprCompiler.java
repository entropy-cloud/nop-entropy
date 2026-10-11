/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.compile;

import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.expr.IExecutableExprCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.xlang.XLangConfigs.CFG_XLANG_TYPE_INFERENCE_ENABLED;

/**
 * {@link IExecutableExprCompiler} 的默认实现（compile 包承载编译管线，经 META-INF/services 装配）。
 * 管线自 {@code XLangExprParser.buildExecutable} 原样迁入：词法作用域分析 → 可选类型推导 →
 * {@link BuildExecutableProcessor} 构建可执行体，行为逐行等价。
 */
public class DefaultExecutableExprCompiler implements IExecutableExprCompiler {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultExecutableExprCompiler.class);

    @Override
    public IExecutableExpression buildExecutable(Expression expr, boolean optimize, IXLangCompileScope scope) {
        expr = new LexicalScopeAnalysis(scope).analyze(expr);

        if (LOG.isTraceEnabled()) {
            PrintResolvedIdentifier print = new PrintResolvedIdentifier();
            print.visit(expr);
            LOG.trace(print.getOutput());
        }

        if (CFG_XLANG_TYPE_INFERENCE_ENABLED.get()) {
            TypeInferenceProcessor typeInference = new TypeInferenceProcessor();
            typeInference.processAST(expr, new TypeInferenceState());
            // 类型推导错误以警告日志形式暴露，不中断编译
            if (typeInference.getErrors().hasErrors()) {
                for (TypeErrorCollector.TypeError typeError : typeInference.getErrors().getErrors()) {
                    LOG.warn("nop_xlang_type_inference_error: {}", typeError);
                }
            }
        }

        return new BuildExecutableProcessor().processAST(expr, scope);
    }
}
