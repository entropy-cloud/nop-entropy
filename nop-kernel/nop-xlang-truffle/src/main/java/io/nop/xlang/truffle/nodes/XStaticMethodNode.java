package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.reflect.IMethodModelCollection;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 类静态方法分派节点（StaticFunctionExecutable 直译；plan I7 Phase 1 §3 <b>既有节点缓存回填
 * ——L2 直达形态</b>）：直持树内编译期常量 {@link IMethodModelCollection}（解释器
 * StaticFunctionExecutable 同一常量，{@code getMethodCollection()} 树内携带），调用
 * {@link XLangSemantics#invokeStaticMethod}（与解释器同一 helper + 同一常量——消除逐调用
 * {@code Class.forName} + 反射模型解析；与 {@code XNewObjectNode} 持 ClassModel 同构模式）。
 * 节点持有常量与源树实例的同一性 = 缓存初始化探针（接线证据，测试断言）。
 */
public final class XStaticMethodNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String className;

    private final String funcName;

    private final boolean optional;

    private final IMethodModelCollection methodCollection;

    private final XExprNode[] args;

    public XStaticMethodNode(SourceLocation loc, String display, String className, String funcName,
                             boolean optional, IMethodModelCollection methodCollection, XExprNode[] args) {
        this.loc = loc;
        this.display = display;
        this.className = className;
        this.funcName = funcName;
        this.optional = optional;
        this.methodCollection = methodCollection;
        this.args = args;
    }

    /** 缓存初始化探针：编译期常量解析产物（与源树实例同一性断言用）。 */
    public IMethodModelCollection getMethodCollection() {
        return methodCollection;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] argValues = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            argValues[i] = args[i].execute(frame);
        }
        XLangContext context = XLangLanguage.currentContext();
        return XLangSemantics.invokeStaticMethod(loc, display, className, funcName, optional,
                methodCollection, argValues, context.requireEvalScope());
    }
}
