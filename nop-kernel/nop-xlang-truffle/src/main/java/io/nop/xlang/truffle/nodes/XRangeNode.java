package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 区间构造节点（RangeExecutable 直译）：起止步按序求值（null 步长表达式翻译为 null 值，
 * helper 内归一为 1），经共享 helper 构造迭代器。
 */
public final class XRangeNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final XExprNode begin;

    private final XExprNode end;

    private final XExprNode step;

    public XRangeNode(SourceLocation loc, String display, XExprNode begin, XExprNode end, XExprNode step) {
        this.loc = loc;
        this.display = display;
        this.begin = begin;
        this.end = end;
        this.step = step;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object beginValue = begin.execute(frame);
        Object endValue = end.execute(frame);
        Object stepValue = step.execute(frame);
        return XLangSemantics.range(loc, display, beginValue, endValue, stepValue);
    }
}
