package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * null 字面量节点（NullExecutable 直译）。
 */
public final class XNullNode extends XExprNode {

    public static final XNullNode INSTANCE = new XNullNode();

    private XNullNode() {
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return null;
    }
}
