package io.nop.xlang.truffle.nodes;

import io.nop.core.lang.eval.ExitMode;

/**
 * ExitMode.RETURN 载体（三值一一对应之一）：携带返回值；函数/程序边界（XLangFunctionRootNode /
 * XLangRootNode）捕获取值，对应解释器 rt.setExitMode(RETURN) + 值经 Seq/调用链回传的语义。
 */
public final class XLReturnException extends XLControlFlowException {

    private static final long serialVersionUID = 1L;

    private final Object value;

    public XLReturnException(Object value) {
        this.value = value;
    }

    @Override
    public ExitMode getExitMode() {
        return ExitMode.RETURN;
    }

    public Object getValue() {
        return value;
    }
}
