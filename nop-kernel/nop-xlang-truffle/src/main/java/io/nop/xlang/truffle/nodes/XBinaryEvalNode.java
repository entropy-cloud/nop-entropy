package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.utils.EvalHelper;

/**
 * 通用二元算子节点（BinaryExecutable 直译）：Plus/Minus 等特化族之外的通用算子
 * （MOD/位运算等）经 {@link EvalHelper#binaryOp} 统一分派（与解释器同一实现来源）。
 */
public final class XBinaryEvalNode extends XExprNode {

    private final XLangOperator operator;

    private final XExprNode left;

    private final XExprNode right;

    public XBinaryEvalNode(XLangOperator operator, XExprNode left, XExprNode right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return EvalHelper.binaryOp(operator, left.execute(frame), right.execute(frame));
    }
}
