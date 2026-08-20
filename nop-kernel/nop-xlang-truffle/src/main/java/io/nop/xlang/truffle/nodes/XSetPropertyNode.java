package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 属性写入节点（SetPropertyExecutable 直译）：接收者与值按序各单次求值，经共享 helper
 * 反射解析并写入，返回写入值（解释器语义）。
 */
public final class XSetPropertyNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String propName;

    private final XExprNode obj;

    private final XExprNode value;

    public XSetPropertyNode(SourceLocation loc, String display, String propName, XExprNode obj,
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
        XLangSemantics.setProperty(loc, display, propName, target, v,
                XLangLanguage.currentContext().requireEvalScope());
        return v;
    }
}
