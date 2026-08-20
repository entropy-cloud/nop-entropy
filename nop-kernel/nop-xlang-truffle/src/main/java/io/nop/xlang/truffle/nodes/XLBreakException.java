package io.nop.xlang.truffle.nodes;

import io.nop.core.lang.eval.ExitMode;

/**
 * ExitMode.BREAK 载体（三值一一对应之一）：无载荷单例；由词法最近循环节点捕获消费
 * （消费即清零，对应解释器循环 setExitMode(null) + break）；穿透视 catch 放行、
 * switch 不消费（live 语义：switch 不检查 exitMode）。
 */
public final class XLBreakException extends XLControlFlowException {

    private static final long serialVersionUID = 1L;

    public static final XLBreakException INSTANCE = new XLBreakException();

    private XLBreakException() {
    }

    @Override
    public ExitMode getExitMode() {
        return ExitMode.BREAK;
    }
}
