package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.xlang.exec.XLangSemantics;

/**
 * slot 自增/自减节点（SelfInc/SelfDec 直译）：返回旧值（解释器语义），新值经共享 helper
 * 复合写回。
 */
public final class XSlotSelfIncNode extends XExprNode {

    private final int slot;

    private final int delta;

    public XSlotSelfIncNode(int slot, int delta) {
        this.slot = slot;
        this.delta = delta;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object old = frame.getValue(slot);
        frame.setObject(slot, XLangSemantics.selfIncValue(old, delta));
        return old;
    }
}
