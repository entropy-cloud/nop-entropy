package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.BindVarExecutable;

/**
 * 闭包体构建节点（BuildClosureBodyExecutable 直译，全仓无产生路径——合成树翻译级覆盖）：
 * 当前帧 sourceSlots 快照 + 构造 {@link BindVarExecutable}（目标槽绑定 captured 后求值 expr
 * 的解释器载体）存入 closureSlot。载荷 expr 为编译期常量（与解释器树同源对象），
 * 存储值语义与解释器逐字对应。
 */
public final class XBuildClosureBodyNode extends XExprNode {

    private final int closureSlot;

    private final int[] sourceSlots;

    private final int[] targetSlots;

    private final IExecutableExpression expr;

    public XBuildClosureBodyNode(int closureSlot, int[] sourceSlots, int[] targetSlots,
                                 IExecutableExpression expr) {
        this.closureSlot = closureSlot;
        this.sourceSlots = sourceSlots;
        this.targetSlots = targetSlots;
        this.expr = expr;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] vars = new Object[sourceSlots.length];
        for (int i = 0; i < sourceSlots.length; i++) {
            vars[i] = frame.getValue(sourceSlots[i]);
        }
        BindVarExecutable executable = new BindVarExecutable(expr.getLocation(), targetSlots, vars, expr);
        frame.setObject(closureSlot, executable);
        return null;
    }
}
