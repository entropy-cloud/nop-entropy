package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 引用 cell 重建节点（RenewReferenceExecutable 直译）：slot 引用 cell 经共享 helper 重建
 * （旧值迁入新 cell），节点返回 null（解释器语义）。
 */
public final class XRefRenewNode extends XExprNode {

    private final int slot;

    public XRefRenewNode(int slot) {
        this.slot = slot;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        frame.setObject(slot, XLangSemantics.renewReference(frame.getValue(slot)));
        return null;
    }
}
