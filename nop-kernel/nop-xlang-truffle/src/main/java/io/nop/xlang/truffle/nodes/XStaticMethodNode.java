package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 类静态方法分派节点（StaticFunctionExecutable 直译）：按 className/funcName 运行时解析
 * 方法集合（与编译期同一解析方式）后统一调用（共享 helper，与 java 生成代码同口径）。
 */
public final class XStaticMethodNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String className;

    private final String funcName;

    private final boolean optional;

    private final XExprNode[] args;

    public XStaticMethodNode(SourceLocation loc, String display, String className, String funcName,
                             boolean optional, XExprNode[] args) {
        this.loc = loc;
        this.display = display;
        this.className = className;
        this.funcName = funcName;
        this.optional = optional;
        this.args = args;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] argValues = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            argValues[i] = args[i].execute(frame);
        }
        XLangContext context = XLangLanguage.currentContext();
        return XLangSemantics.invokeStaticMethodResolved(loc, display, className, funcName, optional,
                argValues, context.requireEvalScope());
    }
}
