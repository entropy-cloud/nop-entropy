/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.debugger.IDebugNotifier;
import io.nop.xlang.debugger.XLangDebugger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 会话路由执行器外壳（D3 §3.4 路由表的三条运行时路由）：
 * 无标记 → 默认会话兜底（IDEA 全局断点语义）；有标记存活 → 该会话执行器；
 * 有标记已销毁 → {@link DestroyedSessionExecutor}（跳过调试钩点、保留取消保护）。
 *
 * <p>零开销红线：无标记路径仅两次 ThreadLocal get + 分支（web 关时不安装本外壳，
 * 注入现状形态的 {@link io.nop.xlang.debugger.DebugExpressionExecutor}——D0 §3.3 开关矩阵行 2）。
 * 函数/标签调用边界重读全局槽位时拿到的仍是本外壳单实例，按当前线程标记重新路由
 * ——同一线程内路由不变（D3 §3.2 双机制论证）。
 *
 * <p>异常事件外壳（item 13，D1 §3.5 evt.exception 产生源）：三分支统一在最外层入口
 * 捕获被调试执行的未捕获异常并上报 notifier（try/catch-log-rethrow，传播行为零改变）；
 * ThreadLocal 守卫保证嵌套重入只报最外层——函数/标签调用边界重入全局槽位的嵌套
 * execute 不重复上报，异常由最外层入口一次性承载。脚本内部 try/catch 捕获的异常
 * 不逃逸出执行边界，不上报（uncaught 语义）。
 */
public final class SessionRoutingExecutor implements IExpressionExecutor {
    static final Logger LOG = LoggerFactory.getLogger(SessionRoutingExecutor.class);

    private final SessionDebugManager manager;

    // 最外层入口守卫（item 13）：嵌套重入不重复上报异常
    private final ThreadLocal<Boolean> exceptionReporting = new ThreadLocal<>();

    public SessionRoutingExecutor(SessionDebugManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public Object execute(IExecutableExpression expr, EvalRuntime rt) {
        DebugSessionHandle handle = DebugSessionScope.currentHandle();
        if (handle == null)
            // default 分支触达：default 会话在 init 时注册（键 "default"）；未 init 时
            // defaultExecutor() fail-fast（item 11 既有形态），非静默直通
            return executeWithReporting(manager.getSession("default"),
                    manager.defaultExecutor(), expr, rt);
        if (handle.isDestroyed())
            return executeWithReporting(handle, manager.destroyedHandle().getDebugExecutor(), expr, rt);
        return executeWithReporting(handle, handle.getDebugExecutor(), expr, rt);
    }

    private Object executeWithReporting(DebugSessionHandle handle, IExpressionExecutor target,
                                        IExecutableExpression expr, EvalRuntime rt) {
        if (Boolean.TRUE.equals(exceptionReporting.get()))
            return target.execute(expr, rt);
        exceptionReporting.set(Boolean.TRUE);
        try {
            return target.execute(expr, rt);
        } catch (Throwable t) {
            notifyException(handle, t);
            throw t;
        } finally {
            exceptionReporting.remove();
        }
    }

    private void notifyException(DebugSessionHandle handle, Throwable t) {
        // plan 2473 审计 F6：REPL 取消/超时是服务契约内结果，不推 evt.exception 噪声
        if (t instanceof ReplCancelledError)
            return;
        try {
            XLangDebugger debugger = handle == null ? null : handle.getDebugger();
            IDebugNotifier notifier = debugger == null ? null : debugger.getNotifier();
            if (notifier == null)
                return;
            String sourcePath = null;
            int line = -1;
            String errorCode = null;
            String message;
            if (t instanceof NopException) {
                NopException ne = (NopException) t;
                SourceLocation loc = ne.getErrorLocation();
                if (loc != null && loc.getLine() > 0) {
                    sourcePath = loc.getPath();
                    line = loc.getLine();
                }
                errorCode = ne.getErrorCode();
                message = ne.getDescription() != null ? ne.getDescription() : errorCode;
            } else {
                message = t.toString();
            }
            if (message == null)
                message = "unknown";
            notifier.notifyException(sourcePath, line, message, errorCode);
        } catch (Exception ex) {
            // 上报失败不得吞掉原始异常（原始异常随后 rethrow 传播）
            LOG.debug("nop.xlang.debugger.notify-exception-fail", ex);
        }
    }
}
