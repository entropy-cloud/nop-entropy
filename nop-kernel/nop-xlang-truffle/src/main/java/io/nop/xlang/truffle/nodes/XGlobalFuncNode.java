package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 注册全局函数分派节点（FunctionExecutable 族直译）：运行时经同一全局注册表
 * {@code EvalGlobalRegistry} 解析后统一调用（与 java 后端生成代码同口径，I2 裁定）。
 */
public final class XGlobalFuncNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String funcName;

    private final XExprNode[] args;

    public XGlobalFuncNode(SourceLocation loc, String display, String funcName, XExprNode[] args) {
        this.loc = loc;
        this.display = display;
        this.funcName = funcName;
        this.args = args;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] argValues = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            argValues[i] = args[i].execute(frame);
        }
        XLangContext context = XLangLanguage.currentContext();
        return XLangSemantics.invokeGlobalFunction(loc, display, funcName, argValues,
                context.requireEvalScope());
    }
}
