package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.convert.ConvertHelper;

/**
 * 分支节点（IfExecutable 直译）：test 经 toTruthy 判定（与解释器同一转换），返回命中分支
 * 求值值（if 为表达式——无分支命中返回 null）。
 */
public final class XIfNode extends XExprNode {

    private final XExprNode test;

    private final XExprNode consequent;

    private final XExprNode alternate;

    public XIfNode(XExprNode test, XExprNode consequent, XExprNode alternate) {
        this.test = test;
        this.consequent = consequent;
        this.alternate = alternate;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        boolean b = ConvertHelper.toTruthy(test.execute(frame));
        if (b) {
            return consequent.execute(frame);
        }
        return alternate == null ? null : alternate.execute(frame);
    }
}
