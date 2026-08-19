package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 逻辑非节点（NotExecutable 直译）：真值转换走共享 helper。
 */
public final class XNotNode extends XExprNode {

    private final XExprNode expr;

    public XNotNode(XExprNode expr) {
        this.expr = expr;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return !XLangSemantics.truthy(expr.execute(frame));
    }
}
