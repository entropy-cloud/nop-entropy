package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalReference;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 引用变量自增/自减节点（ReferenceSelfInc/ReferenceSelfDec 直译）：返回旧值（解释器语义），
 * 新值经共享 helper 复合写回引用 cell。
 */
public final class XRefSelfIncNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String varName;

    private final int slot;

    private final int delta;

    public XRefSelfIncNode(SourceLocation loc, String display, String varName, int slot, int delta) {
        this.loc = loc;
        this.display = display;
        this.varName = varName;
        this.slot = slot;
        this.delta = delta;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        EvalReference ref = XLangSemantics.asRef(loc, display, varName, frame.getValue(slot));
        Object old = ref.getValue();
        ref.setValue(XLangSemantics.selfIncValue(old, delta));
        return old;
    }
}
