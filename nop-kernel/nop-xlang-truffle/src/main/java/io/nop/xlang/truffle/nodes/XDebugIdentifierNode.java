package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.core.lang.eval.EvalReference;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 调试标识符节点（DebugIdentifierExecutable 直译）：与解释器同一查找序——入口帧按名定位
 * （slot 化优先，翻译期自入口 slotNames 解析）→ 回落 scope 按名读取（Q3 残余路径）。
 */
public final class XDebugIdentifierNode extends XExprNode {

    private final int slot;

    private final String varName;

    public XDebugIdentifierNode(int slot, String varName) {
        this.slot = slot;
        this.varName = varName;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (slot >= 0)
            return EvalReference.deRef(frame.getValue(slot));
        return XLangLanguage.currentContext().requireEvalScope().getValue(varName);
    }
}
