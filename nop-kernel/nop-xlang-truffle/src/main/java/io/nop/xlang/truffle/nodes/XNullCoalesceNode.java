package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * null 合并节点（NullCoalesceExecutable 直译）：短路保真——左值无条件单次求值，
 * 右侧仅在左侧为 null 时求值。
 */
public final class XNullCoalesceNode extends XExprNode {

    private final XExprNode left;

    private final XExprNode right;

    public XNullCoalesceNode(XExprNode left, XExprNode right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = left.execute(frame);
        if (v == null)
            return right.execute(frame);
        return v;
    }
}
