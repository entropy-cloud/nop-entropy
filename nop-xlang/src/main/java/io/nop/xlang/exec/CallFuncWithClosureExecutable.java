/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

import static io.nop.xlang.XLangErrors.ERR_EXEC_CALL_FUNC_FAIL;

public class CallFuncWithClosureExecutable extends AbstractExecutable {
    private final String funcName;
    private final String[] slotNames;
    private final IExecutableExpression[] argExprs;
    private IExecutableExpression bodyExpr;
    private final int[] sourceSlots;
    private final int[] targetSlots;

    public CallFuncWithClosureExecutable(SourceLocation loc, String funcName, String[] slotNames,
                                         IExecutableExpression[] argExprs, IExecutableExpression bodyExpr, int[] sourceSlots, int[] targetSlots) {
        super(loc);
        this.funcName = funcName;
        this.slotNames = slotNames;
        this.argExprs = argExprs;
        this.bodyExpr = bodyExpr;
        this.sourceSlots = sourceSlots;
        this.targetSlots = targetSlots;
    }

    public void setBodyExpr(IExecutableExpression bodyExpr) {
        this.bodyExpr = bodyExpr;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(funcName).append('(');
        for (int i = 0, n = argExprs.length; i < n; i++) {
            argExprs[i].display(sb);
            if (i != n - 1)
                sb.append(',');
        }
        sb.append(")");
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        EvalFrame current = rt.getCurrentFrame();
        EvalFrame frame = new EvalFrame(current, slotNames);
        frame.setDisplayInfo(getLocation(), funcName);

        // 这里需要确保argExpr个数与函数定义时的参数个数一致
        for (int i = 0, n = argExprs.length; i < n; i++) {
            Object arg = executor.execute(argExprs[i], rt);
            frame.setArg(i, arg);
        }

        // 从当前环境中捕获闭包变量，然后设置到函数的局部frame中
        for (int i = 0, n = sourceSlots.length; i < n; i++) {
            int closureSlot = sourceSlots[i];
            frame.setStackValue(targetSlots[i], current.getStackValue(closureSlot));
        }

        try {
            rt.pushFrame(frame);
            return executor.execute(bodyExpr, rt);
        } catch (NopException e) {
            e.addXplStack(this);
            throw e;
        } catch (Exception e) {
            throw newError(ERR_EXEC_CALL_FUNC_FAIL, e).forWrap();
        } finally {
            rt.setExitMode(null);
            rt.popFrame();
        }
    }
}