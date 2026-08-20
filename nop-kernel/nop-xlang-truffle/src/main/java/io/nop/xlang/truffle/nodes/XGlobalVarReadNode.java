package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 全局变量按名读取节点（GlobalVarExecutable 直译）：经 context 持有的 scope 链解析全局变量
 * （Q3 残余路径），语义统一走 {@link XLangSemantics} 共享 helper。
 */
public final class XGlobalVarReadNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String varName;

    public XGlobalVarReadNode(SourceLocation loc, String display, String varName) {
        this.loc = loc;
        this.display = display;
        this.varName = varName;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.getGlobalVarValue(loc, display, XLangLanguage.currentContext().requireEvalScope(),
                varName);
    }
}
