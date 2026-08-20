package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * slot 变量绑定节点（BindVarExecutable 直译）：编译期固化 (slot, var) 对，求值时按序写入
 * 帧 slot（slot 化路径）后求值载荷表达式；写入值与解释器一致（vars 原样写入）。
 */
public final class XBindVarNode extends XExprNode {

    private final int[] slots;

    private final Object[] vars;

    private final XExprNode expr;

    public XBindVarNode(int[] slots, Object[] vars, XExprNode expr) {
        this.slots = slots;
        this.vars = vars;
        this.expr = expr;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        for (int i = 0; i < slots.length; i++) {
            frame.setObject(slots[i], vars[i]);
        }
        return expr.execute(frame);
    }
}
