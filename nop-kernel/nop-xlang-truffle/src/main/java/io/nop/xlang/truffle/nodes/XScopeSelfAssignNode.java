package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 作用域变量复合赋值节点（ScopeSelfAssignExecutable 直译）：求值顺序与解释器一致——
 * 先读旧值，再求值 change，最后经共享 helper 复合并写回，返回复合结果。
 */
public final class XScopeSelfAssignNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String varName;

    private final XLangOperator operator;

    private final XExprNode value;

    public XScopeSelfAssignNode(SourceLocation loc, String display, String varName, XLangOperator operator,
                                XExprNode value) {
        this.loc = loc;
        this.display = display;
        this.varName = varName;
        this.operator = operator;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        IEvalScope scope = XLangLanguage.currentContext().requireEvalScope();
        Object old = scope.getValue(varName);
        Object change = value.execute(frame);
        Object result = XLangSemantics.selfAssignValue(loc, display, operator, old, change);
        XLangSemantics.setScopeValue(loc, scope, varName, result);
        return result;
    }
}
