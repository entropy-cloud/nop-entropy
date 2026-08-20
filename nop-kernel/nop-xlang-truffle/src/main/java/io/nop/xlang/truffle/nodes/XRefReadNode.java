package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 引用变量读取节点（ReferenceIdentifierExecutable 直译）：slot 化路径——帧 slot 承载
 * EvalReference cell，读取经共享 helper 解引用（slot 化优先，Q3）。
 */
public final class XRefReadNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String id;

    private final int slot;

    public XRefReadNode(SourceLocation loc, String display, String id, int slot) {
        this.loc = loc;
        this.display = display;
        this.id = id;
        this.slot = slot;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.getRefValue(loc, display, id, frame.getValue(slot));
    }
}
