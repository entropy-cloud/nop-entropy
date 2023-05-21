/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.debugger;

import io.nop.api.core.annotations.core.Name;

import java.util.List;

/**
 * 通过同步线程阻塞来实现简单的调试器。可以很简单的为各类XDSL增加调试功能， 仅需元模型上记录SourceLocation, 在具体每个动作前调用checkBreakpoint即可。
 */
public interface IDebugger extends IBreakpointManager {
    void stepInto();

    void stepOver();

    void stepOut();

    void suspend();

    void resume();

    /**
     * 等待调试器进入挂起状态。 stepInto/stepOver等函数仅仅是发出指令，它们返回时调试器并不一定执行到了调试位置
     */
    void waitSuspended();

    void close();

    /**
     * 暂时忽略所有断点
     */
    void muteBreakpoints(@Name("muted") boolean muted);

    void updateBreakpoints(@Name("bps") List<Breakpoint> bps, @Name("muted") boolean muted);

    boolean isSuspended();

    boolean isBreakpointsMuted();

    List<ThreadInfo> getSuspendedThreads();

    // IBreakpointManager getBreakpointManager();

    void runToPosition(@Name("bp") Breakpoint bp);

    DebugVariable getExprValue(@Name("threadId") long threadId, @Name("frameIndex") int frameIndex, @Name("expr") String expr);

    /**
     * 查看对象的属性值
     *
     * @param expr 对象表达式，它的执行结果返回一个对象
     * @return 返回对象的属性列表
     */
    List<DebugVariable> expandExprValue(@Name("threadId") long threadId, @Name("frameIndex") int frameIndex,
                                        @Name("expr") String expr, @Name("keys") List<DebugValueKey> keys);

    StackInfo getStackInfo(@Name("threadId") long threadId);

    /**
     * 返回IEvalScope中保存的全局变量
     *
     * @param threadId 当前调试线程
     */
    List<DebugVariable> getScopeVariables(@Name("threadId") long threadId);

    List<DebugVariable> getFrameVariables(@Name("threadId") long threadId, @Name("frameIndex") int frameIndex);
}