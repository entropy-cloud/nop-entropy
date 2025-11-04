/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval;

import io.nop.api.core.util.ISourceLocationGetter;

/**
 * 根据XLang抽象语法树编译得到的可执行对象
 */
public interface IExecutableExpression extends ISourceLocationGetter {
    IExecutableExpression[] EMPTY_EXPRS = new IExecutableExpression[0];

    default String display() {
        StringBuilder sb = new StringBuilder();
        display(sb);
        return sb.toString();
    }

    void display(StringBuilder sb);

    default boolean allowBreakPoint() {
        return true;
    }

    /**
     * 是否包含return语句
     */
    boolean containsReturnStatement();

    /**
     * 是否包含break/continue等语句
     */
    boolean containsBreakStatement();

    default boolean isUseExitMode() {
        return !containsReturnStatement() && !containsBreakStatement();
    }

    Object execute(IExpressionExecutor executor, EvalRuntime rt);

    void visit(IExecutableExpressionVisitor visitor);
}
