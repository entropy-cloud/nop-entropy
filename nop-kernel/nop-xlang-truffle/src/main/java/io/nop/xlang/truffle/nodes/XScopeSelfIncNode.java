package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 作用域变量自增/自减节点（ScopeSelfInc/ScopeSelfDec 直译）：经共享 helper 按 delta
 * 复合写回（±1），返回新值（解释器 scopeSelfInc 语义）。
 */
public final class XScopeSelfIncNode extends XExprNode {

    private final SourceLocation loc;

    private final String varName;

    private final int delta;

    public XScopeSelfIncNode(SourceLocation loc, String varName, int delta) {
        this.loc = loc;
        this.varName = varName;
        this.delta = delta;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.scopeSelfInc(loc, XLangLanguage.currentContext().requireEvalScope(), varName, delta);
    }
}
