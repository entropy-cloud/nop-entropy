package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.RootCallTarget;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

/**
 * truffle 函数值（plan I7：函数值载荷下降的运行期载体，implements {@link IEvalFunction}）——
 * 持有被翻译函数体的 {@link RootCallTarget}（一级内联缓存的身份载体，language 实例作用域内
 * 稳定）与<b>捕获时</b>值快照（BuildFuncRef 载体：捕获后外部写不可见；可变共享经 Reference
 * cell 对象按引用传递）。
 *
 * <p>调用协议：invoke/callN → {@code callTarget.call(args, captured)}，由
 * {@link XLangFunctionRootNode} 绑定入口槽并消化 ExitMode 边界（控制流异常不外泄）。
 * 函数值调用点经 {@link XFunctionDispatchNode} 两级内联缓存分派（L1 CallTarget 身份 +
 * L2 DirectCallNode）；mixed-invoke（解释器/宿主代码回调本类型）走本类入口，
 * 与解释器 {@code ExecutableFunction.invoke} 同一调用形态。
 */
public final class XLangTruffleFunction implements IEvalFunction {

    private final RootCallTarget callTarget;

    private final Object[] captured;

    public XLangTruffleFunction(RootCallTarget callTarget, Object[] captured) {
        this.callTarget = callTarget;
        this.captured = captured == null ? new Object[0] : captured;
    }

    public RootCallTarget getCallTarget() {
        return callTarget;
    }

    /** 直达调用实参组装（DirectCallNode/dispatch 命中路径与 invoke 共用）。 */
    public Object[] callArguments(Object[] args, IEvalScope scope) {
        return new Object[]{args, captured};
    }

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return callTarget.call(callArguments(args == null ? new Object[0] : args, scope));
    }

    @Override
    public Object call0(Object thisObj, IEvalScope scope) {
        return callTarget.call(callArguments(new Object[0], scope));
    }

    @Override
    public Object call1(Object thisObj, Object arg, IEvalScope scope) {
        return callTarget.call(callArguments(new Object[]{arg}, scope));
    }

    @Override
    public Object call2(Object thisObj, Object arg1, Object arg2, IEvalScope scope) {
        return callTarget.call(callArguments(new Object[]{arg1, arg2}, scope));
    }

    @Override
    public Object call3(Object thisObj, Object arg1, Object arg2, Object arg3, IEvalScope scope) {
        return callTarget.call(callArguments(new Object[]{arg1, arg2, arg3}, scope));
    }
}
