package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 函数值调用节点（VarFunctionExecutable/VarExecutableFunction 直译）：求值顺序保真——
 * funcExpr 先求值；func 为 null 时实参<b>不求值</b>（与解释器 getFunction → null 检查 →
 * 实参求值一致），null 短路/报错经共享 helper {@link XLangSemantics#callVarFunction}；
 * 非 null 时实参求值后经 {@link XFunctionDispatchNode} 两级内联缓存分派
 * （L1 CallTarget 身份 + L2 DirectCallNode + generic 泛化路径，全模块首个 DSL 缓存用法）。
 *
 * <p>探针访问（{@link #getDispatch()}）= 可观测缓存状态接线证据载体（测试/诊断用，
 * 非语义路径）。
 */
public final class XVarFunctionCallNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final boolean optional;

    private final XExprNode funcExpr;

    private final XExprNode[] args;

    @Child
    private XFunctionDispatchNode dispatch = XFunctionDispatchNodeGen.create();

    public XVarFunctionCallNode(SourceLocation loc, String display, boolean optional,
                                XExprNode funcExpr, XExprNode[] args) {
        this.loc = loc;
        this.display = display;
        this.optional = optional;
        this.funcExpr = funcExpr;
        this.args = args;
    }

    /** 缓存状态探针访问器（接线证据载体）。 */
    public XFunctionDispatchNode getDispatch() {
        return dispatch;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object function = funcExpr.execute(frame);
        if (function == null) {
            // 求值顺序保真：func 为 null 时实参不求值，null 短路/报错语义在共享 helper
            IEvalScope scope = XLangLanguage.currentContext().requireEvalScope();
            return XLangSemantics.callVarFunction(loc, display, optional, null, null, scope);
        }
        Object[] argValues = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            argValues[i] = args[i].execute(frame);
        }
        XLangContext context = XLangLanguage.currentContext();
        return dispatch.executeDispatch(loc, display, optional, context.requireEvalScope(),
                function, argValues);
    }
}
