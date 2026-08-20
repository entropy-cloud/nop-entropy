package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 异常抛出节点（ThrowExceptionExecutable 直译）：值求值后经共享 helper
 * {@link XLangSemantics#throwException}（NopException/Throwable 重抛、其余包装
 * ERR_EXEC_THROW_EXCEPTION——与解释器同一实现来源）。
 */
public final class XThrowExceptionNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final XExprNode expr;

    public XThrowExceptionNode(SourceLocation loc, String display, XExprNode expr) {
        this.loc = loc;
        this.display = display;
        this.expr = expr;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object value = expr.execute(frame);
        XLangSemantics.throwException(loc, display, value);
        return null;
    }
}
