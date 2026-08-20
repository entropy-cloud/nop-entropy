package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * null 判定节点（EqNull/NeNull/StrictEqNull/StrictNeNull 直译）：四变体 live 同实现
 * （宽松与 strict 均为引用 null 判定），经 mode 归并为两分支。
 */
public final class XNullCheckNode extends XExprNode {

    public enum Mode {
        EQ_NULL, NE_NULL
    }

    private final Mode mode;

    private final XExprNode value;

    public XNullCheckNode(Mode mode, XExprNode value) {
        this.mode = mode;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = value.execute(frame);
        return mode == Mode.EQ_NULL ? v == null : v != null;
    }
}
