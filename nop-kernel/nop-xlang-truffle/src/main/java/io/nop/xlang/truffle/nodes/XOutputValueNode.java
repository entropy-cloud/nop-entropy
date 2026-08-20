package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 值输出节点（OutputValueExecutable 直译）：值求值后经 context 窗口绑定输出缓冲
 * {@code value} 调用（求值顺序与解释器一致：先求值后输出）。
 */
public final class XOutputValueNode extends XExprNode {

    private final SourceLocation loc;

    private final XExprNode value;

    public XOutputValueNode(SourceLocation loc, XExprNode value) {
        this.loc = loc;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = value.execute(frame);
        IEvalOutput out = XLangLanguage.currentContext().requireOutput();
        out.value(loc, v);
        return null;
    }
}
