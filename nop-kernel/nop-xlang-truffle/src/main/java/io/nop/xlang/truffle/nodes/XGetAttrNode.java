package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 动态属性名读取节点（GetAttrExecutable 直译）：接收者与属性名按序求值，经共享 helper
 * 反射解析并读取（null 接收者按 optional 语义抛错或返回 null）。
 */
public final class XGetAttrNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String objDisplay;

    private final String attrDisplay;

    private final boolean optional;

    private final XExprNode obj;

    private final XExprNode attr;

    public XGetAttrNode(SourceLocation loc, String display, String objDisplay, String attrDisplay,
                        boolean optional, XExprNode obj, XExprNode attr) {
        this.loc = loc;
        this.display = display;
        this.objDisplay = objDisplay;
        this.attrDisplay = attrDisplay;
        this.optional = optional;
        this.obj = obj;
        this.attr = attr;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object target = obj.execute(frame);
        Object attrName = attr.execute(frame);
        return XLangSemantics.getAttr(loc, display, objDisplay, attrDisplay, optional, target, attrName);
    }
}
