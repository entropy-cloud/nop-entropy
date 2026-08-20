package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 带默认值转换节点（ConvertWithDefaultExecutable 直译）：短路保真——值无条件单次求值，
 * 默认值表达式仅在值为 null 时求值（与解释器一致），经共享 helper 转换。
 */
public final class XConvertWithDefaultNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String funcName;

    private final XExprNode value;

    private final XExprNode defaultValue;

    public XConvertWithDefaultNode(SourceLocation loc, String display, String funcName, XExprNode value,
                                   XExprNode defaultValue) {
        this.loc = loc;
        this.display = display;
        this.funcName = funcName;
        this.value = value;
        this.defaultValue = defaultValue;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = value.execute(frame);
        Object def = null;
        if (v == null)
            def = defaultValue.execute(frame);
        return XLangSemantics.convertWithDefault(loc, display, funcName,
                XLangLanguage.currentContext().requireEvalScope(), v, def);
    }
}
