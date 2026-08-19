package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * 语句位置包装节点（ReturnNullExecutable 直译）：执行子表达式（保留副作用）后返回 null。
 */
public final class XReturnNullNode extends XExprNode {

    private final XExprNode expr;

    public XReturnNullNode(XExprNode expr) {
        this.expr = expr;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        expr.execute(frame);
        return null;
    }
}
