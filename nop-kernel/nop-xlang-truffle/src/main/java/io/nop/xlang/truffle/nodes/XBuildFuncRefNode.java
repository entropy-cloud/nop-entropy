package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.xlang.exec.ExecutableFunction;

/**
 * 函数指针捕获节点（BuildFuncRefExecutable 直译，plan I7 Phase 1 §4 闭包形态）：
 * <b>捕获时</b>急切值拷贝——创建点读取当前帧 sourceSlots 槽当前值快照（对应解释器
 * {@code bindClosureVars}：捕获后外部写不可见；引用槽持 EvalReference 对象 → 按引用拷贝
 * = 可变共享 cell），返回 {@link XLangTruffleFunction}（CallTarget + targetSlots + 快照）。
 */
public final class XBuildFuncRefNode extends XExprNode {

    private final int[] sourceSlots;

    private final RootCallTarget target;

    public XBuildFuncRefNode(ExecutableFunction func, int[] sourceSlots, RootCallTarget target) {
        this.sourceSlots = sourceSlots;
        this.target = target;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] vars = new Object[sourceSlots.length];
        for (int i = 0; i < sourceSlots.length; i++) {
            vars[i] = frame.getValue(sourceSlots[i]);
        }
        return new XLangTruffleFunction(target, vars);
    }
}
