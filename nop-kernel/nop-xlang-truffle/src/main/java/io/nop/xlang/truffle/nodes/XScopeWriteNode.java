package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 作用域变量按名写入节点（ScopeAssignExecutable 直译）：值单次求值后经共享 helper 写入
 * context 持有的 scope 链，返回写入值（解释器语义）。
 */
public final class XScopeWriteNode extends XExprNode {

    private final SourceLocation loc;

    private final String varName;

    private final XExprNode value;

    public XScopeWriteNode(SourceLocation loc, String varName, XExprNode value) {
        this.loc = loc;
        this.varName = varName;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = value.execute(frame);
        XLangSemantics.setScopeValue(loc, XLangLanguage.currentContext().requireEvalScope(), varName, v);
        return v;
    }
}
