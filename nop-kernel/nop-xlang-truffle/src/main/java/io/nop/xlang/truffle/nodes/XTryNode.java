package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * try 节点（TryExecutable 直译）：catch 分支捕获 {@code Exception}、exceptionSlot 承载、
 * 执行 catch 体后<b>吞掉异常继续</b>（JS 语义，与解释器 TryExecutable 一致；catch 体显式
 * {@code throw e} 时由 catch 体自身抛出新异常）。
 *
 * <p>控制流异常交互：body 抛出的 XLControlFlowException 在 catch(Exception) 之前<b>显式
 * 放行</b>（对应解释器 flag 语义下 catch 不因 exitMode 触发）；catch 体/finally 体自身的
 * XLControlFlowException 同样<b>放行重抛</b>（break/continue/return 穿透 try 到达最近的
 * 循环/switch/函数边界，与解释器 flag 存活语义一致）；finally 在 unwind 中执行（Java 原生
 * finally）。
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
            // catch体执行完成后吞掉异常；catch体内的控制流异常（return/break/continue）放行
            return catchExpr.execute(frame);
        } finally {
            if (finallyExpr != null) {
                finallyExpr.execute(frame);
            }
        }
    }
}
