package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 循环变量状态节点（VarStatusExecutable 直译）：集合单次求值，经共享 helper 构造
 * LoopVarStatus（null 集合返回 null 不写 slot），非 null 时写入 slot；返回状态对象。
 */
public final class XVarStatusNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String itemsDisplay;

    private final int slot;

    private final XExprNode items;

    public XVarStatusNode(SourceLocation loc, String display, String itemsDisplay, int slot, XExprNode items) {
        this.loc = loc;
        this.display = display;
        this.itemsDisplay = itemsDisplay;
        this.slot = slot;
        this.items = items;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object vs = XLangSemantics.varStatus(loc, display, itemsDisplay, items.execute(frame));
        if (vs != null)
            frame.setObject(slot, vs);
        return vs;
    }
}
