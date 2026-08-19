package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;

/**
 * null 守卫节点（GuardNotNullExecutable 直译）：成员调用接收者非空断言，异常语义
 * （错误码 + loc + display）走共享 helper。
 */
public final class XGuardNotNullNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final XExprNode expr;

    public XGuardNotNullNode(SourceLocation loc, String display, XExprNode expr) {
        this.loc = loc;
        this.display = display;
        this.expr = expr;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.guardNotNull(loc, display, expr.execute(frame));
    }
}
