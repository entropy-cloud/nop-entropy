package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 属性复合赋值节点（SelfAssignPropertyExecutable 直译）：接收者与值按序各单次求值，
 * 经共享 helper 复合写入，返回复合结果。
 */
public final class XSelfAssignPropertyNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String propName;

    private final XLangOperator operator;

    private final XExprNode obj;

    private final XExprNode value;

    public XSelfAssignPropertyNode(SourceLocation loc, String display, String propName, XLangOperator operator,
                                   XExprNode obj, XExprNode value) {
        this.loc = loc;
        this.display = display;
        this.propName = propName;
        this.operator = operator;
        this.obj = obj;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object target = obj.execute(frame);
        Object v = value.execute(frame);
        return XLangSemantics.selfAssignProperty(loc, display, propName, operator, target, v,
                XLangLanguage.currentContext().requireEvalScope());
    }
}
