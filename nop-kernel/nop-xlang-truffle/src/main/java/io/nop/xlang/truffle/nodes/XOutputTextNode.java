package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 文本输出节点（OutputTextExecutable 直译）：经 context 窗口绑定输出缓冲 {@code text} 调用
 * （$out 通路——与 java 列第二隐参同一缓冲对象来源，harness RecordingEvalOutput）。
 */
public final class XOutputTextNode extends XExprNode {

    private final SourceLocation loc;

    private final String text;

    public XOutputTextNode(SourceLocation loc, String text) {
        this.loc = loc;
        this.text = text;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        IEvalOutput out = XLangLanguage.currentContext().requireOutput();
        out.text(loc, text);
        return null;
    }
}
