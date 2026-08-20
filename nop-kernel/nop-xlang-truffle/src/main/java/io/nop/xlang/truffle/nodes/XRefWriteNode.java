package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 引用变量写入节点（ReferenceAssignExecutable 直译）：值单次求值后经共享 helper 写入
 * slot 持有的引用 cell（cell 不存在时新建），返回写入值（解释器语义）。
 */
public final class XRefWriteNode extends XExprNode {

    private final int slot;

    private final XExprNode value;

    public XRefWriteNode(int slot, XExprNode value) {
        this.slot = slot;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = value.execute(frame);
        frame.setObject(slot, XLangSemantics.setRefValue(frame.getValue(slot), v));
        return v;
    }
}
