package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.functions.EvalFunctionalAdapter;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 函数式适配节点（FunctionalAdapterExecutable 直译，plan I7：truffle 无跨 JVM 约束 →
 * 真实翻译）：载荷 IEvalFunction 为编译期常量（树内对象，与解释器同源），求值构造
 * {@link EvalFunctionalAdapter}（绑定本次求值作用域）——与解释器逐字对应。
 */
public final class XFunctionalAdapterNode extends XExprNode {

    private final SourceLocation loc;

    private final IEvalFunction function;

    public XFunctionalAdapterNode(SourceLocation loc, IEvalFunction function) {
        this.loc = loc;
        this.function = function;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        XLangContext context = XLangLanguage.currentContext();
        return new EvalFunctionalAdapter(loc, function, context.requireEvalScope());
    }
}
