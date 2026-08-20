package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalReference;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 引用变量复合赋值节点（ReferenceSelfAssignExecutable 直译）：求值顺序与解释器一致——
 * 先解析 slot 引用 cell，再求值 change，最后复合写回，返回复合结果。
 */
public final class XRefSelfAssignNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String varName;

    private final int slot;

    private final XLangOperator operator;

    private final XExprNode value;

    public XRefSelfAssignNode(SourceLocation loc, String display, String varName, int slot,
                              XLangOperator operator, XExprNode value) {
        this.loc = loc;
        this.display = display;
        this.varName = varName;
        this.slot = slot;
        this.operator = operator;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        EvalReference ref = XLangSemantics.asRef(loc, display, varName, frame.getValue(slot));
        Object change = value.execute(frame);
        Object result = XLangSemantics.selfAssignValue(loc, display, operator, ref.getValue(), change);
        ref.setValue(result);
        return result;
    }
}
