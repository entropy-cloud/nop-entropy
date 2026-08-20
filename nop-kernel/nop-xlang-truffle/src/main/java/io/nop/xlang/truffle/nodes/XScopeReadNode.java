package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 作用域变量按名读取节点（ScopeIdentifierExecutable 直译）：翻译期无法 slot 化的按名访问
 * 走 context 持有的 scope 链查找（设计 truffle 02 §四 Q3 残余路径）；scope 仅在求值窗口内
 * 经 {@link XLangContext#requireEvalScope()} 存取（窗口外 fail-fast），节点不存 context 数据。
 */
public final class XScopeReadNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String varName;

    public XScopeReadNode(SourceLocation loc, String display, String varName) {
        this.loc = loc;
        this.display = display;
        this.varName = varName;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.getScopeValue(loc, display, XLangLanguage.currentContext().requireEvalScope(),
                varName);
    }
}
