/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.expr;

import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;

/**
 * 表达式编译管线接缝：把 {@code Expression} 编译为可执行体（词法作用域分析 → 可选类型推导 → 构建
 * {@code IExecutableExpression}）。
 *
 * <p>expr 包不得直接依赖 compile 包（包级分层治理红线，见 xlang-package-layering 设计文档），
 * 默认实现由 compile 包经 {@code META-INF/services} 提供；未注册时 {@code buildExecutable} 以
 * {@code ERR_EXEC_COMPILER_NOT_REGISTERED} 响亮失败。
 */
public interface IExecutableExprCompiler {
    IExecutableExpression buildExecutable(Expression expr, boolean optimize, IXLangCompileScope scope);
}
