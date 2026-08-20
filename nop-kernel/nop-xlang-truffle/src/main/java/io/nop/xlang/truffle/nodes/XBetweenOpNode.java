package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.core.model.query.FilterOp;

/**
 * 区间谓词节点（BetweenOpExecutable 直译）：值/下界/上界按序求值，经 FilterOp 统一
 * between 谓词判定（排除边界标志翻译期固化）。
 */
public final class XBetweenOpNode extends XExprNode {

    private final FilterOp filterOp;

    private final boolean excludeMin;

    private final boolean excludeMax;

    private final XExprNode value;

    private final XExprNode min;

    private final XExprNode max;

    public XBetweenOpNode(FilterOp filterOp, boolean excludeMin, boolean excludeMax, XExprNode value,
                          XExprNode min, XExprNode max) {
        this.filterOp = filterOp;
        this.excludeMin = excludeMin;
        this.excludeMax = excludeMax;
        this.value = value;
        this.min = min;
        this.max = max;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = value.execute(frame);
        Object minValue = min.execute(frame);
        Object maxValue = max.execute(frame);
        return filterOp.getBetweenOperator().test(v, minValue, maxValue, excludeMin, excludeMax);
    }
}
