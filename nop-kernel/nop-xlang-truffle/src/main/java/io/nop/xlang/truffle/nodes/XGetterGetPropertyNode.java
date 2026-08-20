package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 已解析 getter 属性读取节点（GetterGetPropertyExecutable 直译）：接收者先求值，经共享
 * helper 调用编译期解析的 getter。
 */
public final class XGetterGetPropertyNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String propName;

    private final XExprNode obj;

    public XGetterGetPropertyNode(SourceLocation loc, String display, String propName, XExprNode obj) {
        this.loc = loc;
        this.display = display;
        this.propName = propName;
        this.obj = obj;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.getterGetProperty(loc, display, propName,
                obj.execute(frame), XLangLanguage.currentContext().requireEvalScope());
    }
}
