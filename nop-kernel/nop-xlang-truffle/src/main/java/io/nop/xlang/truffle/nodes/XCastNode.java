package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 类型转换节点（CastExecutable 直译）：翻译期固化目标类名，运行时经共享 helper 转换
 * （null/default 语义与解释器同源）。
 */
public final class XCastNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String className;

    private final XExprNode value;

    public XCastNode(SourceLocation loc, String display, String className, XExprNode value) {
        this.loc = loc;
        this.display = display;
        this.className = className;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.castValue(loc, display, XLangLanguage.currentContext().requireEvalScope(),
                className, value.execute(frame));
    }
}
