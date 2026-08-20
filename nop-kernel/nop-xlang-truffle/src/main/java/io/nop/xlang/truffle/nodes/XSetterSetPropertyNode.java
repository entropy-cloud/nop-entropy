package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 已解析 setter 属性写入节点（SetterSetPropertyExecutable 直译）：与 {@link XSetPropertyNode}
 * 同一共享 helper（按 propName 同一解析），返回写入值。
 */
public final class XSetterSetPropertyNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String propName;

    private final XExprNode obj;

    private final XExprNode value;

    public XSetterSetPropertyNode(SourceLocation loc, String display, String propName, XExprNode obj,
                                  XExprNode value) {
        this.loc = loc;
        this.display = display;
        this.propName = propName;
        this.obj = obj;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object target = obj.execute(frame);
        Object v = value.execute(frame);
        XLangSemantics.setterSetProperty(loc, display, propName, target, v,
                XLangLanguage.currentContext().requireEvalScope());
        return v;
    }
}
