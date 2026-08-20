package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 静态属性读取节点（StaticGetterGetPropertyExecutable 直译）：翻译期固化类名/属性名，
 * 经共享 helper 运行时解析读取。
 */
public final class XStaticGetPropertyNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String className;

    private final String propName;

    public XStaticGetPropertyNode(SourceLocation loc, String display, String className, String propName) {
        this.loc = loc;
        this.display = display;
        this.className = className;
        this.propName = propName;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.getStaticProperty(loc, display, className, propName,
                XLangLanguage.currentContext().requireEvalScope());
    }
}
