package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.xlang.ast.XLangEscapeMode;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 转义输出节点（EscapeOutputExecutable 直译）：值求值后经共享 helper
 * {@link XLangSemantics#escapeOutput}（escapeMode 为编译期常量枚举）。
 */
public final class XEscapeOutputNode extends XExprNode {

    private final SourceLocation loc;

    private final XLangEscapeMode escapeMode;

    private final XExprNode value;

    public XEscapeOutputNode(SourceLocation loc, XLangEscapeMode escapeMode, XExprNode value) {
        this.loc = loc;
        this.escapeMode = escapeMode;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = value.execute(frame);
        IEvalOutput out = XLangLanguage.currentContext().requireOutput();
        XLangSemantics.escapeOutput(loc, out, escapeMode, v);
        return null;
    }
}
