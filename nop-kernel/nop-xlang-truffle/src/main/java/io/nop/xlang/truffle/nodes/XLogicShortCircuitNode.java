package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 逻辑短路节点（AndExecutable/OrExecutable 直译）：左侧求值后按真值决定右侧是否求值，
 * 返回原值（非布尔化）——与解释器逐分支求值顺序一致。
 */
public final class XLogicShortCircuitNode extends XExprNode {

    private final boolean and;

    private final XExprNode left;

    private final XExprNode right;

    public XLogicShortCircuitNode(boolean and, XExprNode left, XExprNode right) {
        this.and = and;
        this.left = left;
        this.right = right;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v1 = left.execute(frame);
        if (XLangSemantics.truthy(v1) == and)
            return right.execute(frame);
        return v1;
    }
}
