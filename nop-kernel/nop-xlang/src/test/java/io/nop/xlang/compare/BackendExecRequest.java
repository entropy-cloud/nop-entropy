package io.nop.xlang.compare;

import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;

/**
 * 后端列执行入口的请求：同一单元的多列执行使用<b>同一棵 Executable 树实例</b>
 * （非源文本重新编译，差异只可能来自后端），以及独立新建、按单元声明等价初始化的求值现场
 * （IEvalScope + 输出缓冲）。副作用快照逐列在各自现场上获取，禁止跨列共享可变现场。
 */
public final class BackendExecRequest {
    private final CompareUnit unit;
    private final IExecutableExpression expr;
    private final IEvalScope scope;
    private final IEvalOutput out;

    public BackendExecRequest(CompareUnit unit, IExecutableExpression expr, IEvalScope scope, IEvalOutput out) {
        this.unit = unit;
        this.expr = expr;
        this.scope = scope;
        this.out = out;
    }

    public CompareUnit getUnit() {
        return unit;
    }

    /**
     * 对拍对象：同一棵树实例。列必须执行本实例（或以本实例为翻译源），并在证据中如实上报。
     */
    public IExecutableExpression getExpr() {
        return expr;
    }

    /**
     * 本列独占的求值现场（已按单元声明等价初始化）。禁止跨列共享可变现场。
     */
    public IEvalScope getScope() {
        return scope;
    }

    /**
     * 本列独占的输出缓冲（harness 提供的录制 wrapper，副作用快照来源之一）。
     */
    public IEvalOutput getOut() {
        return out;
    }
}
