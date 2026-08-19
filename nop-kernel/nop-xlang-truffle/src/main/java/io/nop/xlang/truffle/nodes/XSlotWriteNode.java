package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * slot 写入节点（SlotAssignExecutable 直译）：写入值并返回该值（解释器语义）。
 * 写入模式按帧布局声明的 slot kind 分派——primitive kind 槽经对应 typed setter 写入
 * （kind 推断前提 = 全部写入源为同族字面量，见 FrameLayoutMapper），Object 槽经
 * {@code setObject}。
 */
public final class XSlotWriteNode extends XExprNode {

    private final int slot;

    private final FrameSlotKind kind;

    private final XExprNode value;

    private final String varName;

    public XSlotWriteNode(int slot, FrameSlotKind kind, String varName, XExprNode value) {
        this.slot = slot;
        this.kind = kind;
        this.varName = varName;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = value.execute(frame);
        switch (kind) {
            case Int:
                frame.setInt(slot, (Integer) v);
                break;
            case Long:
                frame.setLong(slot, (Long) v);
                break;
            case Double:
                frame.setDouble(slot, (Double) v);
                break;
            case Float:
                frame.setFloat(slot, (Float) v);
                break;
            case Boolean:
                frame.setBoolean(slot, (Boolean) v);
                break;
            default:
                frame.setObject(slot, v);
                break;
        }
        return v;
    }

    public String getVarName() {
        return varName;
    }
}
