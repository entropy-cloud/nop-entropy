package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.commons.util.MathHelper;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 一元算子节点（Neg/BitNot/TypeOf 直译）：无标量差异的同构一元语义经 op 分派，实现统一
 * 走共享 helper（MathHelper / XLangSemantics，与解释器同一实现来源）。
 */
public final class XUnaryOpNode extends XExprNode {

    public enum Op {
        NEG, BIT_NOT, TYPE_OF
    }

    private final Op op;

    private final XExprNode value;

    public XUnaryOpNode(Op op, XExprNode value) {
        this.op = op;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = value.execute(frame);
        switch (op) {
            case NEG:
                return MathHelper.neg(v);
            case BIT_NOT:
                return MathHelper.bneg(v);
            case TYPE_OF:
            default:
                return XLangSemantics.typeOf(v);
        }
    }
}
