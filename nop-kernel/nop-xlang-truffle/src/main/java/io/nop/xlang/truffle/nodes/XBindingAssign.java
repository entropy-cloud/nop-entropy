package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 解构绑定赋值单元（AssignIdentifier/PropBinding 直译）：slot>=0 时按 useRef 走引用 cell
 * 写或直写帧 slot（slot 化路径）；slot<0 时按名写入 scope 链（Q3 残余路径）。语义与解释器
 * {@code AssignIdentifier.assign} 同源（经共享 helper）。
 */
public final class XBindingAssign {

    private final SourceLocation loc;

    private final int slot;

    private final String varName;

    private final boolean useRef;

    public XBindingAssign(SourceLocation loc, int slot, String varName, boolean useRef) {
        this.loc = loc;
        this.slot = slot;
        this.varName = varName;
        this.useRef = useRef;
    }

    public int getSlot() {
        return slot;
    }

    public void assign(VirtualFrame frame, Object value, IEvalScope scope) {
        if (slot >= 0) {
            if (useRef) {
                frame.setObject(slot, XLangSemantics.setRefValue(frame.getValue(slot), value));
            } else {
                frame.setObject(slot, value);
            }
        } else {
            XLangSemantics.setScopeValue(loc, scope, varName, value);
        }
    }
}
