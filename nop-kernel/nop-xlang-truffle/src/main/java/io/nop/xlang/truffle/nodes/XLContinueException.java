package io.nop.xlang.truffle.nodes;

import io.nop.core.lang.eval.ExitMode;

/**
 * ExitMode.CONTINUE 载体（三值一一对应之一）：无载荷单例；由词法最近循环节点捕获消费
 * （消费即清零后进入下一迭代，对应解释器循环 setExitMode(null) + continue）。
 */
public final class XLContinueException extends XLControlFlowException {

    private static final long serialVersionUID = 1L;

    public static final XLContinueException INSTANCE = new XLContinueException();

    private XLContinueException() {
    }

    @Override
    public ExitMode getExitMode() {
        return ExitMode.CONTINUE;
    }
}
