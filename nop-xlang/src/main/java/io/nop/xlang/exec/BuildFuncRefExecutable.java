/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExpressionExecutor;

/**
 * 函数作为指针被保存或者传递时，需要保存使用到的当前环境中的闭包变量
 */
public class BuildFuncRefExecutable extends AbstractExecutable {
    private final ExecutableFunction func;
    private final int[] sourceSlots; // 当前frame中哪些变量需要随着函数指针传递
    private final int[] targetSlots; // 调用函数时，把闭包变量设置到调用frame的哪些slot中

    public BuildFuncRefExecutable(SourceLocation loc, ExecutableFunction func, int[] sourceSlots, int[] targetSlots) {
        super(loc);
        this.func = func;
        if (sourceSlots.length > 0)
            Guard.checkArgument(sourceSlots[0] >= 0);
        this.sourceSlots = sourceSlots;
        this.targetSlots = targetSlots;
    }

    public static BuildFuncRefExecutable build(SourceLocation loc, ExecutableFunction func, int[] sourceSlots,
                                               int[] targetSlots) {
        return new BuildFuncRefExecutable(loc, Guard.notNull(func, "func"), sourceSlots, targetSlots);
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(func.getFuncName());
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        // 从当前环境中捕获闭包变量
        EvalFrame frame = rt.getCurrentFrame();
        Object[] vars = new Object[sourceSlots.length];
        for (int i = 0, n = sourceSlots.length; i < n; i++) {
            int closureSlot = sourceSlots[i];
            vars[i] = frame.getStackValue(closureSlot);
        }
        return func.bindClosureVars(getLocation(), targetSlots, vars);
    }
}
