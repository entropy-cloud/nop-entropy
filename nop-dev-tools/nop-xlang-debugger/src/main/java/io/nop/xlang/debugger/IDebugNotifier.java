/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;

public interface IDebugNotifier {
    void notifyWarn(String message, Object... args);

    void notifyLog(SuspendedThread thread, String sourcePath, int line, String message);

    void notifySuspend(SuspendedThread thread, SourceLocation loc, EvalRuntime rt);

    void notifyStepInto(SuspendedThread thread, SourceLocation loc, EvalRuntime rt);

    void notifyStepOver(SuspendedThread thread, SourceLocation loc, EvalRuntime rt);

    void notifyStepOut(SuspendedThread thread, SourceLocation loc, EvalRuntime rt);

    void notifyBreakAt(SuspendedThread thread, SourceLocation loc, EvalRuntime rt);

    /**
     * 租约到期自动放行事件（受控扩展，D1 §3.5 evt.leaseExpired 产生源）。
     * 默认空实现——非破坏扩展（item 10）。
     */
    default void notifyLeaseExpired(java.util.List<Long> threadIds) {
    }

    /**
     * 用户 resume 放行事件（受控扩展，D1 §3.5 evt.resumed 产生源；与 leaseExpired 互斥）。
     * 默认空实现——非破坏扩展（item 10）。
     */
    default void notifyResumed(java.util.List<Long> threadIds, String cause) {
    }

    /**
     * 被调试执行单元的未捕获异常事件（受控扩展，D1 §3.5 evt.exception 产生源，item 13）：
     * web 激活时由 SessionRoutingExecutor 最外层入口上报（嵌套重入只报最外层）；
     * sourcePath/line 为异常定位（无定位时 null / 负值，D1 schema 可选字段）。
     * 默认空实现——非破坏扩展。
     */
    default void notifyException(String sourcePath, int line, String message, String errorCode) {
    }
}
