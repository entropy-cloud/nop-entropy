package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * 字符串拼接节点（ConcatExecutable 直译）：逐元素按序求值，非 null 追加
 * （求值顺序与解释器一致）。
 */
public final class XConcatNode extends XExprNode {

    private final XExprNode[] exprs;

    public XConcatNode(XExprNode[] exprs) {
        this.exprs = exprs;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        StringBuilder sb = new StringBuilder();
        for (XExprNode expr : exprs) {
            Object value = expr.execute(frame);
            if (value != null)
                sb.append(value);
        }
        return sb.toString();
    }
}
