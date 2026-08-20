package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangLanguage;

import java.util.Set;

/**
 * xml 扩展属性输出节点（OutputXmlExtAttrsExecutable 直译）：attrs 求值后经共享 helper
 * {@link XLangSemantics#outputXmlExtAttrs}（排除名集合为编译期常量，非 Map 报错语义同一来源）。
 */
public final class XOutputXmlExtAttrsNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final Set<String> excludeNames;

    private final XExprNode attrs;

    public XOutputXmlExtAttrsNode(SourceLocation loc, String display, Set<String> excludeNames, XExprNode attrs) {
        this.loc = loc;
        this.display = display;
        this.excludeNames = excludeNames;
        this.attrs = attrs;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = attrs.execute(frame);
        IEvalOutput out = XLangLanguage.currentContext().requireOutput();
        XLangSemantics.outputXmlExtAttrs(loc, display, out, excludeNames, v);
        return null;
    }
}
