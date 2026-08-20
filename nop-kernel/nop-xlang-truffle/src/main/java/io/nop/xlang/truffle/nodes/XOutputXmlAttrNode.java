package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * xml 属性输出节点（OutputXmlAttrExecutable 直译）：值求值后经共享 helper
 * {@link XLangSemantics#outputXmlAttr} 输出（name 转义语义同一实现来源）。
 */
public final class XOutputXmlAttrNode extends XExprNode {

    private final SourceLocation loc;

    private final String name;

    private final XExprNode value;

    public XOutputXmlAttrNode(SourceLocation loc, String name, XExprNode value) {
        this.loc = loc;
        this.name = name;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = value.execute(frame);
        IEvalOutput out = XLangLanguage.currentContext().requireOutput();
        XLangSemantics.outputXmlAttr(loc, out, name, v);
        return null;
    }
}
