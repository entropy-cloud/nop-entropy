package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 错误码抛出节点（ThrowErrorCodeExecutable 直译）：error 求值 →（error 非 NopException/
 * Throwable 时）params 求值（求值顺序与解释器一致）→ 共享 helper
 * {@link XLangSemantics#throwErrorCode}。
 */
public final class XThrowErrorCodeNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final XExprNode errorExpr;

    private final XExprNode paramsExpr;

    public XThrowErrorCodeNode(SourceLocation loc, String display, XExprNode errorExpr, XExprNode paramsExpr) {
        this.loc = loc;
        this.display = display;
        this.errorExpr = errorExpr;
        this.paramsExpr = paramsExpr;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object error = errorExpr.execute(frame);
        Object params = null;
        if (!(error instanceof NopException) && !(error instanceof Throwable) && paramsExpr != null)
            params = paramsExpr.execute(frame);
        XLangSemantics.throwErrorCode(loc, null, display, error, params);
        return null;
    }
}
