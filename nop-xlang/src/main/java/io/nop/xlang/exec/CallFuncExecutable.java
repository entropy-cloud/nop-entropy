/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

import static io.nop.xlang.XLangErrors.ERR_EXEC_CALL_FUNC_FAIL;

public class CallFuncExecutable extends AbstractExecutable {
    private final String funcName;
    private final String[] slotNames;
    private final IExecutableExpression[] argExprs;
    private IExecutableExpression bodyExpr;
    private boolean allowBreakpoint = true;

    public CallFuncExecutable(SourceLocation loc, String funcName, String[] slotNames, IExecutableExpression[] argExprs,
                              IExecutableExpression bodyExpr) {
        super(loc);
        this.funcName = funcName;
        this.slotNames = slotNames;
        this.argExprs = argExprs;
        this.bodyExpr = bodyExpr;
    }

    public void setBodyExpr(IExecutableExpression bodyExpr) {
        this.bodyExpr = bodyExpr;
    }

    @Override
    public boolean allowBreakPoint() {
        return allowBreakpoint;
    }

    public void setAllowBreakpoint(boolean allowBreakpoint) {
        this.allowBreakpoint = allowBreakpoint;
    }

    // tell cpd to start ignoring code - CPD-OFF
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
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        EvalFrame current = scope.getCurrentFrame();
        EvalFrame frame = new EvalFrame(current, slotNames);
        frame.setDisplayInfo(getLocation(), funcName);
        for (int i = 0, n = argExprs.length; i < n; i++) {
            Object arg = executor.execute(argExprs[i], scope);
            frame.setArg(i, arg);
        }
        try {
            scope.pushFrame(frame);
            return executor.execute(bodyExpr, scope);
        } catch (NopException e) {
            e.addXplStack(this);
            throw e;
        } catch (Exception e) {
            throw newError(ERR_EXEC_CALL_FUNC_FAIL, e).forWrap();
        } finally {
            scope.setExitMode(null);
            scope.popFrame();
        }
    }
    // resume CPD analysis - CPD-ON
}