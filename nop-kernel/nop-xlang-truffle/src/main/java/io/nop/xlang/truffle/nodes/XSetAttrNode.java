package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 动态属性名写入节点（SetAttrExecutable 直译）：接收者/属性名/值按序各单次求值，经共享
 * helper 写入，返回写入值（解释器语义）。
 */
public final class XSetAttrNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String attrDisplay;

    private final XExprNode obj;

    private final XExprNode attr;

    private final XExprNode value;

    public XSetAttrNode(SourceLocation loc, String display, String attrDisplay, XExprNode obj,
                        XExprNode attr, XExprNode value) {
        this.loc = loc;
        this.display = display;
        this.attrDisplay = attrDisplay;
        this.obj = obj;
        this.attr = attr;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object target = obj.execute(frame);
        Object attrName = attr.execute(frame);
        Object v = value.execute(frame);
        XLangSemantics.setAttr(loc, display, attrDisplay, target, attrName, v);
        return v;
    }
}
