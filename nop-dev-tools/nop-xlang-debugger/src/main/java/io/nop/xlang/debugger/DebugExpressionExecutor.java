/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class DebugExpressionExecutor implements IExpressionExecutor {
    private final IXLangDebugger debugger;
    private final io.nop.xlang.debugger.session.ReplDeadlineChannel deadlineChannel;

    public DebugExpressionExecutor(IXLangDebugger debugger) {
        this(debugger, null);
    }

    /**
     * 融合执行器形态（plan 2473，D6 §3.2）：deadline/取消检查并入执行器入口——
     * 每入口先查（不 gate allowBreakPoint，对齐 LintDeadlineExecutor"更宽"先例）再走
     * 既有调试钩点分支后自线程化。融合而非串行：平台 executor 约定自线程化，外挂
     * 装饰器与调试执行器串行不可两全（外装饰器自线程化则路由/断点旁路；每入口委托
     * 则只有根入口一次检查——while(true) 永不复查）。
     *
     * @param deadlineChannel 可空——null 时行为与融合前完全一致（默认会话/IDEA 通道零改动）
     */
    public DebugExpressionExecutor(IXLangDebugger debugger,
                                   io.nop.xlang.debugger.session.ReplDeadlineChannel deadlineChannel) {
        this.debugger = debugger;
        this.deadlineChannel = deadlineChannel;
    }

    @Override
    public Object execute(IExecutableExpression expr, EvalRuntime rt) {
        if (deadlineChannel != null)
            deadlineChannel.checkDeadline(rt);
        if (!expr.allowBreakPoint())
            return expr.execute(this, rt);

        debugger.checkBreakpoint(expr.getLocation(), rt);
        return expr.execute(this, rt);
    }
}