/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.xpl.xlib.XplLibTagCompiler;

/**
 * 将IExecutableExpression包装为IEvalFunction接口。 函数定义可以直接编译得到ExecutableFunction，除了expr属性之外，其他属性都是根据函数定义可以直接得到
 */
public class LazyCompiledExecutableFunction extends AbstractExecutable {
    private final String funcName;
    private final IExecutableExpression[] argExprs;
    private final XplLibTagCompiler.LazyCompiledFunction body;

    public LazyCompiledExecutableFunction(SourceLocation loc, String funcName,
                                          IExecutableExpression[] argExprs,
                                          XplLibTagCompiler.LazyCompiledFunction body) {
        super(loc);
        this.funcName = funcName;
        this.argExprs = argExprs;
        this.body = body;
    }

    public String getFuncName() {
        return funcName;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(funcName).append("(").append(argExprs.length).append(")");
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        ExecutableFunction fn = getCompiled();
        EvalFrame frame = new EvalFrame(rt.getCurrentFrame(), fn.getSlotNames());
        frame.setDisplayInfo(getLocation(), funcName);

        int argCount = argExprs.length;
        for (int i = 0; i < argCount; i++) {
            frame.setArg(i, argExprs[i].execute(executor, rt));
        }

        rt.pushFrame(frame);
        try {
            return executor.execute(fn.getBody(), rt);
        } finally {
            rt.setExitMode(null);
            rt.popFrame();
        }
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        if (visitor.onVisitExpr(this)) {
            getCompiled().getBody().visit(visitor);
            for (IExecutableExpression argExpr : argExprs) {
                argExpr.visit(visitor);
            }
            visitor.onEndVisitExpr(this);
        }
    }

    public ExecutableFunction getCompiled() {
        return ((ExecutableFunction) body.getCompiledFn());
    }
}