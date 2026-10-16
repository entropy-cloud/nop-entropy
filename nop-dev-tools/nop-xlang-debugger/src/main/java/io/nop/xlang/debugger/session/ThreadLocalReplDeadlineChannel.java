/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.core.lang.eval.EvalRuntime;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_REPL_CANCELLED;
import static io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_REPL_TIMEOUT;

/**
 * REPL deadline/取消通道（D6 §3.2/§3.3，plan 2473）：基于执行线程 ThreadLocal 的
 * deadline + **共享 volatile 取消旗标**（plan 评审 F3 修订——死循环占死执行线程期间
 * 外部调用方无法经 ThreadLocal 翻旗标，取消语义必须跨线程可见；deadline 保持
 * ThreadLocal 使服务请求间互不干扰）。脚本不可见（不经 scope——通用编译器下脚本
 * 可按名读写删除 scope 键，D6 §3.2 取消通道裁定）。
 *
 * <p>生命周期：服务层 try/finally 使用 begin→end；cancel 供管理/测试面跨线程调用
 * （一期无 HTTP 取消端点）。检查点 = 融合执行器（DebugExpressionExecutor）与
 * destroyed 分支（DestroyedSessionExecutor）每入口，不 gate allowBreakPoint。
 */
public class ThreadLocalReplDeadlineChannel implements ReplDeadlineChannel {
    private static final ThreadLocalReplDeadlineChannel INSTANCE = new ThreadLocalReplDeadlineChannel();

    public static ThreadLocalReplDeadlineChannel instance() {
        return INSTANCE;
    }

    private final ThreadLocal<Long> deadlineMillis = new ThreadLocal<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private ThreadLocalReplDeadlineChannel() {
    }

    /** 设定当前线程的执行 deadline（绝对时钟，断点挂起时间计入——D6 §3.4 一期限制）。 */
    public void begin(long deadlineEpochMillis) {
        cancelled.set(false);
        deadlineMillis.set(deadlineEpochMillis);
    }

    /** 跨线程取消：翻共享旗标，执行线程在下一个融合执行器入口抛 REPL_CANCELLED。 */
    public void cancel() {
        cancelled.set(true);
    }

    /** 结束当前线程的 deadline 窗口（finally 必调；复位共享旗标）。 */
    public void end() {
        deadlineMillis.remove();
        cancelled.set(false);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void checkDeadline(EvalRuntime rt) {
        if (cancelled.get())
            throw new ReplCancelledError(ERR_XLANG_DEBUGGER_REPL_CANCELLED.getErrorCode(),
                    "repl execution cancelled");
        Long deadline = deadlineMillis.get();
        if (deadline != null && System.currentTimeMillis() >= deadline)
            throw new ReplCancelledError(ERR_XLANG_DEBUGGER_REPL_TIMEOUT.getErrorCode(),
                    "repl execution timeout");
    }
}
