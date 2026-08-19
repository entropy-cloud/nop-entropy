package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.core.model.query.FilterOp;

/**
 * 过滤比较谓词节点（CompareOpExecutable 直译）：FilterOp 枚举统一谓词源，与解释器同一
 * {@code getBiPredicate()} 实现。
 */
public final class XCompareOpNode extends XExprNode {

    private final FilterOp filterOp;

    private final XExprNode left;

    private final XExprNode right;

    public XCompareOpNode(FilterOp filterOp, XExprNode left, XExprNode right) {
        this.filterOp = filterOp;
        this.left = left;
        this.right = right;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return filterOp.getBiPredicate().test(left.execute(frame), right.execute(frame));
    }
}
