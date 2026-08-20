package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.core.lang.eval.EvalReference;

/**
 * 引用 slot 初始化/增强节点（InitRefSlot/EnhanceRefSlot 直译）：INIT 建空引用 cell，
 * ENHANCE 以 slot 现值建新引用 cell；节点返回 null（解释器语义）。
 */
public final class XRefSlotInitNode extends XExprNode {

    public enum Mode {
        INIT, ENHANCE
    }

    private final int slot;

    private final Mode mode;

    public XRefSlotInitNode(int slot, Mode mode) {
        this.slot = slot;
        this.mode = mode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (mode == Mode.INIT) {
            frame.setObject(slot, new EvalReference(null));
        } else {
            frame.setObject(slot, new EvalReference(frame.getValue(slot)));
        }
        return null;
    }
}
