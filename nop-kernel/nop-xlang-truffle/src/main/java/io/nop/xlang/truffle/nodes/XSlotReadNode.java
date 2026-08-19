package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * slot 读取节点（SlotIdentifierExecutable 直译）：VirtualFrame slot 下标一一对应前端
 * slot 布局；{@code getValue} 统一取装箱值（未初始化 slot 返回 null，与解释器
 * EvalFrame 语义一致）。
 */
public final class XSlotReadNode extends XExprNode {

    private final int slot;

    private final String id;

    public XSlotReadNode(int slot, String id) {
        this.slot = slot;
        this.id = id;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return frame.getValue(slot);
    }

    public String getId() {
        return id;
    }
}
