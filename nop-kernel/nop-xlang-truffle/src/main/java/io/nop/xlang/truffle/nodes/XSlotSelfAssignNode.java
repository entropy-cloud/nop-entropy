package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.exec.XLangSemantics;

/**
 * slot 复合赋值节点（SelfAssignExecutable 直译）：求值顺序与解释器一致——先读旧值，
 * 再求值 change，最后经共享 helper 复合写回，返回新值。
 */
public final class XSlotSelfAssignNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final int slot;

    private final XLangOperator operator;

    private final XExprNode value;

    public XSlotSelfAssignNode(SourceLocation loc, String display, int slot, XLangOperator operator,
                               XExprNode value) {
        this.loc = loc;
        this.display = display;
        this.slot = slot;
        this.operator = operator;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object old = frame.getValue(slot);
        Object change = value.execute(frame);
        Object result = XLangSemantics.selfAssignValue(loc, display, operator, old, change);
        frame.setObject(slot, result);
        return result;
    }
}
