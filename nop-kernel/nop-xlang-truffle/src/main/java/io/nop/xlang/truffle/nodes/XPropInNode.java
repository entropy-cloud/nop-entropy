package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 属性存在性判定节点（PropInExecutable 直译）：左侧属性名与右侧 bean 按序求值，
 * 经共享 helper 判定（null 操作数恒 false）。
 */
public final class XPropInNode extends XExprNode {

    private final XExprNode left;

    private final XExprNode right;

    public XPropInNode(XExprNode left, XExprNode right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.propIn(left.execute(frame), right.execute(frame));
    }
}
