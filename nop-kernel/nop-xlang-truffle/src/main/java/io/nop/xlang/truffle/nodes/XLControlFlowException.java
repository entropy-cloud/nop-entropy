package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.nodes.ControlFlowException;
import io.nop.core.lang.eval.ExitMode;

/**
 * ExitMode 控制流异常族基类（plan I7 Phase 1 裁定：三值一一对应，SL 模式）：
 * {@code ExitMode.RETURN/BREAK/CONTINUE} → {@link XLReturnException}/{@link XLBreakException}/
 * {@link XLContinueException}（与 Truffle 嵌入侧 Context Exit 无关不对接，设计 truffle 02 §三）。
 *
 * <p>载体选型 = {@link ControlFlowException} 子类（Truffle 惯例，引擎认可的非局部跳转形态，
 * 不落 stack trace）。注意 {@code ControlFlowException extends RuntimeException} 仍属
 * {@code Exception}——传播路径上全部 {@code catch (Exception)} 包装点必须显式放行（XTryNode
 * 放行 / 函数边界消化使 CallFunc 族 wrap 点不可达 / 换缓冲回调经 ExitMode cell 抑制 /
 * 根节点消费），盘点见 plan I7 Execution Notes §1。
 */
public abstract class XLControlFlowException extends ControlFlowException {

    private static final long serialVersionUID = 1L;

    XLControlFlowException() {
    }

    /** 对应的 ExitMode 三值（与解释器 rt.exitMode 语义一一对应）。 */
    public abstract ExitMode getExitMode();

    /**
     * 换缓冲类节点调用点的 pending exit 分派（I4 ExitMode cell 协议的 truffle 载体适配）：
     * cell 空 → 返回收集值；RETURN → XLReturn(收集值)；BREAK/CONTINUE → 词法循环内重抛对应异常
     * （由循环消费 = 解释器"循环消费后清零"），无循环 → XLReturn(收集值)（解释器边界清零吞没 +
     * 收集值为终值的合并对应）。
     */
    public static Object dispatchPendingExit(ExitMode[] exitCell, Object value, boolean inLoop) {
        ExitMode mode = exitCell[0];
        if (mode == null)
            return value;
        if (mode == ExitMode.RETURN)
            throw new XLReturnException(value);
        if (inLoop) {
            if (mode == ExitMode.BREAK)
                throw XLBreakException.INSTANCE;
            throw XLContinueException.INSTANCE;
        }
        throw new XLReturnException(value);
    }
}
