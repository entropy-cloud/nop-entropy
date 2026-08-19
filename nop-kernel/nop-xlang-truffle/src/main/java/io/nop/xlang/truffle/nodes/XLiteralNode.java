package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * 字面量节点（LiteralExecutable 直译）：返回编译期固化的常量值。
 */
public final class XLiteralNode extends XExprNode {

    private final Object value;

    public XLiteralNode(Object value) {
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return value;
    }
}
