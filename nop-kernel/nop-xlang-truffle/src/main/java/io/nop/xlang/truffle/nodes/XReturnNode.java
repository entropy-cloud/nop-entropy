package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * return 节点（ReturnExecutable 直译）：求值返回值后抛出 {@link XLReturnException}
 * （ExitMode.RETURN 载体，携带值）——函数/程序边界捕获取值（= 解释器值经 Seq/调用链回传）；
 * 循环穿透（= 解释器循环 return ret）。无表达式形态返回 null。
 */
public final class XReturnNode extends XExprNode {

    private final XExprNode expr;

    public XReturnNode(XExprNode expr) {
        this.expr = expr;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        throw new XLReturnException(expr == null ? null : expr.execute(frame));
    }
}
