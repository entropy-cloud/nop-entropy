package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.exceptions.NopException;

/**
 * try 节点（TryExecutable 直译，plan I7 Phase 1 §1 Try 交互裁定）：catch 分支捕获
 * {@code Exception}、exceptionSlot 承载、执行 catch 体后<b>必然</b> {@code NopException.adapt}
 * 重抛（live 语义忠实保留）。
 *
 * <p>控制流异常交互：body 抛出的 XLControlFlowException 在 catch(Exception) 之前<b>显式
 * 放行</b>（对应解释器 flag 语义下 catch 不因 exitMode 触发）；finally 在 unwind 中执行
 * （Java 原生 finally）。catch 体/finally 体自身抛出的控制流异常被吞入局部 cell——解释器
 * 对应形态为 flag 置位后被必然重抛的原始异常覆盖（异常胜出，flag 死亡）；已知残余边缘
 * （finally 内多语句 Seq 的 flag 停走不可经异常载体复现）无 corpus 形态，记录于 plan
 * Execution Notes §1。
 */
public final class XTryNode extends XExprNode {

    private final XExprNode body;

    private final int exceptionSlot;

    private final XExprNode catchExpr;

    private final XExprNode finallyExpr;

    public XTryNode(XExprNode body, int exceptionSlot, XExprNode catchExpr, XExprNode finallyExpr) {
        this.body = body;
        this.exceptionSlot = exceptionSlot;
        this.catchExpr = catchExpr;
        this.finallyExpr = finallyExpr;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return body.execute(frame);
        } catch (XLControlFlowException e) {
            // 显式放行：控制流异常不触发 catch 分支（解释器 flag 语义下 catch 仅因 Exception 触发）
            throw e;
        } catch (Exception e) {
            if (catchExpr == null)
                throw e;
            if (exceptionSlot >= 0) {
                frame.setObject(exceptionSlot, e);
            }
            try {
                catchExpr.execute(frame);
            } catch (XLControlFlowException cf) {
                // 解释器 flag 语义：catch 体控制流置位后被必然重抛的原始异常覆盖（异常胜出）
            }
            throw NopException.adapt(e);
        } finally {
            if (finallyExpr != null) {
                try {
                    finallyExpr.execute(frame);
                } catch (XLControlFlowException cf) {
                    // 同上：原始异常（或穿透中的控制流异常）胜出，finally 体内的控制流置位不外泄
                }
            }
        }
    }
}
