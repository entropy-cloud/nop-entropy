package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 局部/闭包函数直达调用节点（非根 CallFuncExecutable / CallFuncWithClosureExecutable /
 * LazyCompiledExecutableFunction 直译，plan I7 Phase 1 §3 L2 直达形态）：被调目标 =
 * 翻译期已知的函数体 RootNode CallTarget（编译期常量，无分派多态，静态
 * {@link DirectCallNode}——PE 可内联）。
 *
 * <p>求值次序与解释器一一对应：实参（调用者帧）→ 闭包捕获值（调用者帧 sourceSlots 当前值，
 * 仅 CallFuncWithClosure 载体）→ 直调（被调帧绑定次序见 {@link XLangFunctionRootNode}）。
 *
 * <p>异常包装（CallFunc/CallFuncWithClosure 载体）：被调体异常经共享 helper
 * {@link XLangSemantics#wrapCallFuncException} 包装重抛（与解释器 catch 分支同码）；
 * LazyCompiled 载体无包装（live 无 catch 分支）；控制流异常已在被调函数边界消化，不进入包装。
 */
public final class XLocalCallNode extends XExprNode {

    private final IExecutableExpression sourceNode;

    private final SourceLocation loc;

    private final String display;

    private final XExprNode[] args;

    private final int[] sourceSlots;

    private final boolean wrapCallException;

    private final RootCallTarget target;

    @Child
    private DirectCallNode callNode;

    public XLocalCallNode(IExecutableExpression sourceNode, SourceLocation loc, String display,
                          RootCallTarget target, XExprNode[] args, int[] sourceSlots,
                          boolean wrapCallException) {
        this.sourceNode = sourceNode;
        this.loc = loc;
        this.display = display;
        this.args = args;
        this.sourceSlots = sourceSlots;
        this.wrapCallException = wrapCallException;
        this.target = target;
        this.callNode = Truffle.getRuntime().createDirectCallNode(target);
    }

    /** 直达调用目标（L2 直达形态证据 / 探针访问器）。 */
    public RootCallTarget getTarget() {
        return target;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] argValues = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            argValues[i] = args[i].execute(frame);
        }
        Object[] captured = new Object[sourceSlots.length];
        for (int i = 0; i < sourceSlots.length; i++) {
            captured[i] = frame.getValue(sourceSlots[i]);
        }
        try {
            return callNode.call(argValues, captured);
        } catch (Exception e) {
            if (!wrapCallException)
                throw e;
            throw XLangSemantics.wrapCallFuncException(sourceNode, loc, display, e);
        }
    }
}
