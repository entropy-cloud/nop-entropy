package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 宿主实例方法分派节点（ObjFunctionExecutable 族直译）：接收者先求值，null 时短路返回
 * null（实参不求值，与解释器逐分支求值顺序一致）；否则经共享 helper 解析并调用。
 */
public final class XObjMethodNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String funcName;

    private final XExprNode objExpr;

    private final XExprNode[] args;

    public XObjMethodNode(SourceLocation loc, String display, String funcName, XExprNode objExpr,
                          XExprNode[] args) {
        this.loc = loc;
        this.display = display;
        this.funcName = funcName;
        this.objExpr = objExpr;
        this.args = args;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object obj = objExpr.execute(frame);
        if (obj == null)
            return null;

        Object[] argValues = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            argValues[i] = args[i].execute(frame);
        }
        XLangContext context = XLangLanguage.currentContext();
        return XLangSemantics.invokeObjMethod(loc, display, obj, funcName, argValues,
                context.requireEvalScope());
    }
}
