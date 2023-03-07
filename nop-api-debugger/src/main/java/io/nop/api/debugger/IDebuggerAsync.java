/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.debugger;

import io.nop.api.core.util.SourceLocation;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface IDebuggerAsync extends IDebugger {
    CompletionStage<Void> addBreakpointAsync(Breakpoint bp);

    CompletionStage<Void> removeBreakpointAsync(Breakpoint bp);

    /**
     * 清除当前所有断点，然后设置新的断点
     *
     * @param bps 断点集合
     */
    CompletionStage<Void> setBreakpointsAsync(List<Breakpoint> bps);

    CompletionStage<List<Breakpoint>> getBreakpointsAsync();

    CompletionStage<Void> clearBreakpointsAsync();

    CompletionStage<Breakpoint> getBreakpointAtAsync(SourceLocation loc);

    CompletionStage<Void> stepIntoAsync();

    CompletionStage<Void> stepOverAsync();

    CompletionStage<Void> stepOutAsync();

    CompletionStage<Void> suspendAsync();

    CompletionStage<Void> resumeAsync();

    /**
     * 等待调试器进入挂起状态。 stepInto/stepOver等函数仅仅是发出指令，它们返回时调试器并不一定执行到了调试位置
     */
    CompletionStage<Void> waitSuspendedAsync();

    CompletionStage<Void> closeAsync();

    /**
     * 暂时忽略所有断点
     */
    CompletionStage<Void> muteBreakpointsAsync(boolean muted);

    CompletionStage<Void> updateBreakpointsAsync(List<Breakpoint> bps, boolean muted);

    CompletionStage<Boolean> isSuspendedAsync();

    CompletionStage<Boolean> isBreakpointsMutedAsync();

    CompletionStage<List<ThreadInfo>> getSuspendedThreadsAsync();

    // IBreakpointManager getBreakpointManager();

    CompletionStage<Void> runToPositionAsync(Breakpoint bp);

    CompletionStage<DebugVariable> getExprValueAsync(long threadId, int frameIndex, String expr);

    /**
     * 查看对象的属性值
     *
     * @param expr 对象表达式，它的执行结果返回一个对象
     * @return 返回对象的属性列表
     */
    CompletionStage<List<DebugVariable>> expandExprValueAsync(long threadId, int frameIndex, String expr,
                                                              List<DebugValueKey> keys);

    CompletionStage<StackInfo> getStackInfoAsync(long threadId);

    /**
     * 返回IEvalScope中保存的全局变量
     *
     * @param threadId 当前调试线程
     */
    CompletionStage<List<DebugVariable>> getScopeVariablesAsync(long threadId);

    CompletionStage<List<DebugVariable>> getFrameVariablesAsync(long threadId, int frameIndex);
}
