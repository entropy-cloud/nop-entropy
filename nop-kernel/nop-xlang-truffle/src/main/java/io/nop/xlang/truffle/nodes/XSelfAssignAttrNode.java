package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 动态属性名复合赋值节点（SelfAssignAttrExecutable 直译）：接收者/属性名/值按序各单次
 * 求值，经共享 helper 复合写入，返回复合结果。
 */
public final class XSelfAssignAttrNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String attrDisplay;

    private final XLangOperator operator;

    private final XExprNode obj;

    private final XExprNode attr;

    private final XExprNode value;

    public XSelfAssignAttrNode(SourceLocation loc, String display, String attrDisplay, XLangOperator operator,
                               XExprNode obj, XExprNode attr, XExprNode value) {
        this.loc = loc;
        this.display = display;
        this.attrDisplay = attrDisplay;
        this.operator = operator;
        this.obj = obj;
        this.attr = attr;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object target = obj.execute(frame);
        Object attrName = attr.execute(frame);
        Object v = value.execute(frame);
        return XLangSemantics.selfAssignAttr(loc, display, attrDisplay, operator, target, attrName, v);
    }
}
