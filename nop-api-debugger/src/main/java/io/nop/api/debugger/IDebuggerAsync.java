/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.debugger;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.util.SourceLocation;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface IDebuggerAsync extends IDebugger {
    CompletionStage<Void> addBreakpointAsync(@Name("bp") Breakpoint bp);

    CompletionStage<Void> removeBreakpointAsync(@Name("bp") Breakpoint bp);

    /**
     * 清除当前所有断点，然后设置新的断点
     *
     * @param bps 断点集合
     */
    CompletionStage<Void> setBreakpointsAsync(@Name("bps") List<Breakpoint> bps);

    CompletionStage<List<Breakpoint>> getBreakpointsAsync();

    CompletionStage<Void> clearBreakpointsAsync();

    CompletionStage<Breakpoint> getBreakpointAtAsync(@Name("loc") SourceLocation loc);

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
    CompletionStage<Void> muteBreakpointsAsync(@Name("muted") boolean muted);

    CompletionStage<Void> updateBreakpointsAsync(@Name("bps") List<Breakpoint> bps, @Name("muted") boolean muted);

    CompletionStage<Boolean> isSuspendedAsync();

    CompletionStage<Boolean> isBreakpointsMutedAsync();

    CompletionStage<List<ThreadInfo>> getSuspendedThreadsAsync();

    // IBreakpointManager getBreakpointManager();

    CompletionStage<Void> runToPositionAsync(@Name("bp") Breakpoint bp);

    CompletionStage<DebugVariable> getExprValueAsync(@Name("threadId") long threadId, @Name("frameIndex") int frameIndex,
                                                     @Name("expr") String expr);

    /**
     * 查看对象的属性值
     *
     * @param expr 对象表达式，它的执行结果返回一个对象
     * @return 返回对象的属性列表
     */
    CompletionStage<List<DebugVariable>> expandExprValueAsync(@Name("threadId") long threadId,
                                                              @Name("frameIndex") int frameIndex, @Name("expr") String expr,
                                                              @Name("keys") List<DebugValueKey> keys);

    CompletionStage<StackInfo> getStackInfoAsync(@Name("threadId") long threadId);

    /**
     * 返回IEvalScope中保存的全局变量
     *
     * @param threadId 当前调试线程
     */
    CompletionStage<List<DebugVariable>> getScopeVariablesAsync(@Name("threadId") long threadId);

    CompletionStage<List<DebugVariable>> getFrameVariablesAsync(@Name("threadId") long threadId, @Name("frameIndex") int frameIndex);
}
