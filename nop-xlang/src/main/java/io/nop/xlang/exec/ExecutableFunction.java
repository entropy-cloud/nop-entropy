/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.api.XLang;

import static io.nop.xlang.XLangErrors.ARG_DEF_LOC;
import static io.nop.xlang.XLangErrors.ARG_EXPR;
import static io.nop.xlang.XLangErrors.ARG_FUNC_NAME;
import static io.nop.xlang.XLangErrors.ARG_MAX_COUNT;
import static io.nop.xlang.XLangErrors.ARG_MIN_COUNT;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TOO_FEW_ARGS;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TOO_MANY_ARGS;

/**
 * 将IExecutableExpression包装为IEvalFunction接口。 函数定义可以直接编译得到ExecutableFunction，除了expr属性之外，其他属性都是根据函数定义可以直接得到
 */
public class ExecutableFunction extends AbstractExecutable implements IEvalFunction, ISourceLocationGetter {
    private final SourceLocation defLoc;
    private final String funcName;
    private final int argCount;
    private final String[] slotNames;
    private final int demandArgCount;
    private final IExecutableExpression[] defaultArgValues;
    private IExecutableExpression body;

    public ExecutableFunction(SourceLocation loc, SourceLocation defLoc, String funcName, int argCount,
                              int demandArgCount, String[] slotNames, IExecutableExpression[] defaultArgValues,
                              IExecutableExpression body) {
        super(loc);
        Guard.checkArgument(demandArgCount <= argCount, "demandArgCount must be less than argCount");
        Guard.checkArgument(argCount <= slotNames.length, "argCount must be less than slotCount");
        Guard.checkArgument(demandArgCount + defaultArgValues.length == argCount,
                "each optional arg must has a default value");
        this.defLoc = defLoc;
        this.funcName = funcName;
        this.argCount = argCount;
        this.demandArgCount = demandArgCount;
        this.slotNames = slotNames;
        this.defaultArgValues = defaultArgValues;
        this.body = body;
    }

    public String[] getSlotNames() {
        return slotNames;
    }

    public int getDemandArgCount() {
        return demandArgCount;
    }

    public IExecutableExpression[] getDefaultArgValues() {
        return defaultArgValues;
    }

    public int getArgCount() {
        return argCount;
    }

    public String getFuncName() {
        return funcName;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(funcName).append("(").append(argCount).append(")");
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        EvalFrame frame = new EvalFrame(rt.getCurrentFrame(), slotNames);
        frame.setDisplayInfo(getLocation(), funcName);

        for (int i = 0; i < argCount; i++) {
            frame.setArg(i, defaultArgValues[i].execute(executor, rt));
        }

        rt.pushFrame(frame);
        try {
            return executor.execute(body, rt);
        } finally {
            rt.setExitMode(null);
            rt.popFrame();
        }
    }

    public Object executeWithArgExprs(IExpressionExecutor executor, IExecutableExpression[] argExprs, EvalRuntime rt) {
        EvalFrame frame = new EvalFrame(rt.getCurrentFrame(), slotNames);
        frame.setDisplayInfo(getLocation(), funcName);

        for (int i = 0, n = argExprs.length; i < n; i++) {
            frame.setArg(i, argExprs[i].execute(executor, rt));
        }

        for (int i = argExprs.length; i < argCount; i++) {
            frame.setArg(i, defaultArgValues[i - demandArgCount].execute(executor, rt));
        }

        rt.pushFrame(frame);
        try {
            return executor.execute(body, rt);
        } finally {
            rt.setExitMode(null);
            rt.popFrame();
        }
    }

    public Object executeWithArgs(IExpressionExecutor executor, Object[] args, EvalRuntime rt) {
        EvalFrame frame = new EvalFrame(rt.getCurrentFrame(), slotNames);
        frame.setDisplayInfo(getLocation(), funcName);

        for (int i = 0, n = args.length; i < n; i++) {
            frame.setArg(i, args[i]);
        }

        for (int i = args.length; i < argCount; i++) {
            frame.setArg(i, defaultArgValues[i - demandArgCount].execute(executor, rt));
        }

        rt.pushFrame(frame);
        try {
            return executor.execute(body, rt);
        } finally {
            rt.setExitMode(null);
            rt.popFrame();
        }
    }

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        EvalFrame frame = new EvalFrame(null, slotNames);
        frame.setDisplayInfo(getLocation(), funcName);

        int nArg = args.length;
        // BuildExecutableProcessor生成函数调用的时候会校验参数个数，这里可以省略
        // checkArgCount(getLocation(), nArg);

        for (int i = 0; i < nArg; i++) {
            frame.setArg(i, args[i]);
        }

        IExpressionExecutor executor = XLang.getExecutor();
        EvalRuntime rt = new EvalRuntime(scope);

        for (int i = nArg; i < argCount; i++) {
            frame.setArg(i, defaultArgValues[i - demandArgCount].execute(executor, rt));
        }

        rt.pushFrame(frame);
        return executor.execute(body, rt);
    }

    public void checkArgCount(SourceLocation loc, IExecutableExpression[] argExprs) {
        int nArg = argExprs.length;
        if (nArg > argCount) {
            throw new NopEvalException(ERR_EXEC_TOO_MANY_ARGS).loc(loc).param(ARG_FUNC_NAME, funcName)
                    .param(ARG_EXPR, displayCall(argExprs)).param(ARG_MAX_COUNT, argCount).param(ARG_DEF_LOC, defLoc);
        }

        if (nArg < demandArgCount) {
            throw new NopEvalException(ERR_EXEC_TOO_FEW_ARGS).loc(loc).param(ARG_EXPR, displayCall(argExprs))
                    .param(ARG_FUNC_NAME, funcName).param(ARG_MIN_COUNT, demandArgCount).param(ARG_DEF_LOC, defLoc);
        }
    }

    String displayCall(IExecutableExpression[] argExprs) {
        StringBuilder sb = new StringBuilder();
        sb.append(funcName).append('(');
        for (int i = 0, n = argExprs.length; i < n; i++) {
            argExprs[i].display(sb);
            if (i != n - 1)
                sb.append(',');
        }
        sb.append(")");
        return sb.toString();
    }

    public IExecutableExpression getBody() {
        return body;
    }

    public void setBody(IExecutableExpression body) {
        this.body = body;
    }

    public ExecutableFunction withArgs(SourceLocation loc, IExecutableExpression[] argExprs) {
        checkArgCount(loc, argExprs);
        IExecutableExpression[] fullExprs = new IExecutableExpression[argCount];
        System.arraycopy(argExprs, 0, fullExprs, 0, argExprs.length);
        System.arraycopy(defaultArgValues, argExprs.length - demandArgCount, fullExprs,
                argExprs.length, argCount - argExprs.length);
        return new ExecutableFunction(loc, defLoc, funcName, argCount, 0, slotNames, fullExprs, body);
    }

    public ExecutableFunction bindClosureVars(SourceLocation loc, int[] targetSlots, Object[] values) {
        return new ExecutableFunction(loc, defLoc, funcName, argCount, demandArgCount, slotNames, defaultArgValues,
                new BindVarExecutable(body.getLocation(), targetSlots, values, body));
    }

    @Override
    public Object call0(Object thisObj, IEvalScope scope) {
        EvalFrame frame = new EvalFrame(null, slotNames);
        frame.setDisplayInfo(getLocation(), funcName);

        IExpressionExecutor executor = XLang.getExecutor();
        EvalRuntime rt = new EvalRuntime(scope);

        for (int i = 0; i < argCount; i++) {
            frame.setArg(i, defaultArgValues[i - demandArgCount].execute(executor, rt));
        }
        rt.pushFrame(frame);
        return executor.execute(body, rt);
    }

    @Override
    public Object call1(Object thisObj, Object arg, IEvalScope scope) {
        EvalFrame frame = new EvalFrame(null, slotNames);
        frame.setDisplayInfo(getLocation(), funcName);

        if (argCount < 1) {
            throw new NopEvalException(ERR_EXEC_TOO_MANY_ARGS).source(body).param(ARG_FUNC_NAME, funcName)
                    .param(ARG_EXPR, display()).param(ARG_MAX_COUNT, argCount);
        }
        frame.setArg(0, arg);

        IExpressionExecutor executor = XLang.getExecutor();
        EvalRuntime rt = new EvalRuntime(scope);

        for (int i = 1; i < argCount; i++) {
            frame.setArg(i, defaultArgValues[i - demandArgCount].execute(executor, rt));
        }
        rt.pushFrame(frame);
        return executor.execute(body, rt);
    }

    @Override
    public Object call2(Object thisObj, Object arg1, Object arg2, IEvalScope scope) {
        EvalFrame frame = new EvalFrame(null, slotNames);
        frame.setDisplayInfo(getLocation(), funcName);

        if (argCount < 2) {
            throw new NopEvalException(ERR_EXEC_TOO_MANY_ARGS).source(body).param(ARG_EXPR, display())
                    .param(ARG_FUNC_NAME, funcName).param(ARG_MAX_COUNT, argCount);
        }
        frame.setArg(0, arg1);
        frame.setArg(1, arg2);

        IExpressionExecutor executor = XLang.getExecutor();
        EvalRuntime rt = new EvalRuntime(scope);

        for (int i = 2; i < argCount; i++) {
            frame.setArg(i, defaultArgValues[i - demandArgCount].execute(executor, rt));
        }
        rt.pushFrame(frame);
        return executor.execute(body, rt);
    }

    @Override
    public Object call3(Object thisObj, Object arg1, Object arg2, Object arg3, IEvalScope scope) {
        EvalFrame frame = new EvalFrame(null, slotNames);
        frame.setDisplayInfo(getLocation(), funcName);

        if (argCount < 3) {
            throw new NopEvalException(ERR_EXEC_TOO_MANY_ARGS).source(body).param(ARG_EXPR, display())
                    .param(ARG_FUNC_NAME, funcName).param(ARG_MAX_COUNT, argCount).param(ARG_DEF_LOC, defLoc);
        }
        frame.setArg(0, arg1);
        frame.setArg(1, arg2);
        frame.setArg(2, arg3);

        IExpressionExecutor executor = XLang.getExecutor();
        EvalRuntime rt = new EvalRuntime(scope);

        for (int i = 3; i < argCount; i++) {
            frame.setArg(i, defaultArgValues[i - demandArgCount].execute(executor, rt));
        }
        rt.pushFrame(frame);
        return executor.execute(body, rt);
    }
}