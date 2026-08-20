package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 属性读取节点（GetPropertyExecutable 直译）：接收者先求值，经共享 helper 反射解析并读取
 * （null 接收者按 optional 语义抛错或返回 null，与解释器同源）。
 */
public final class XGetPropertyNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String objDisplay;

    private final boolean optional;

    private final String propName;

    private final XExprNode obj;

    public XGetPropertyNode(SourceLocation loc, String display, String objDisplay, boolean optional,
                           String propName, XExprNode obj) {
        this.loc = loc;
        this.display = display;
        this.objDisplay = objDisplay;
        this.optional = optional;
        this.propName = propName;
        this.obj = obj;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.getProperty(loc, display, objDisplay, optional, propName,
                obj.execute(frame), XLangLanguage.currentContext().requireEvalScope());
    }
}
