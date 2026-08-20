package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 转换函数调用节点（ConvertExecutable 直译）：按 {@code $xxx} 函数名经共享 helper 解析
 * 同一 converter 并转换。
 */
public final class XConvertNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String funcName;

    private final XExprNode value;

    public XConvertNode(SourceLocation loc, String display, String funcName, XExprNode value) {
        this.loc = loc;
        this.display = display;
        this.funcName = funcName;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.convertValue(loc, display, funcName,
                XLangLanguage.currentContext().requireEvalScope(), value.execute(frame));
    }
}
