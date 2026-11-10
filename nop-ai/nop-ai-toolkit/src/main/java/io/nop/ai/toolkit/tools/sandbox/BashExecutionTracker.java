package io.nop.ai.toolkit.tools.sandbox;

import io.nop.commons.util.IoHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plan 2285 (WI4): callId-addressed registry of in-flight bash executions.
 * Sandbox implementations register on {@code execute} entry (process handle +
 * incremental output buffer reference) and unregister in the {@code finally}
 * path, so heartbeat progress snapshots and abandon-time process cancellation
 * can address a running command by its tool callId (design
 * nop-ai-agent-async-tool-execution.md §三.6 进度证据接缝).
 *
 * <p>Thread safety: concurrent executions register/lookup from different
 * threads — the map is concurrent; each entry's buffer is an atomic
 * reference maintained by the sandbox's reader thread.
 */
public final class BashExecutionTracker {

    private static final int TAIL_CHARS = 2000;

    private static final class InFlight {
        final Process process;
        final AtomicReference<StringBuilder> capturedRef;
        /** extra cancellation hook (e.g. docker container name) — nullable */
        final Runnable cancelAction;
        /**
         * 3.2-C6-009（plan 2557 裁定 F）：cancel-pending 标记——销毁段失败时注册项
         * 回填保留，标记表示「已请求取消但销毁未完成」，二次 cancel 可重试。
         */
        volatile boolean cancelPending;

        InFlight(Process process, AtomicReference<StringBuilder> capturedRef, Runnable cancelAction) {
            this.process = process;
            this.capturedRef = capturedRef;
            this.cancelAction = cancelAction;
        }
    }

    private final Map<String, InFlight> inFlight = new ConcurrentHashMap<>();

    /**
     * Registers an in-flight execution. Later registrations for the same
     * callId replace earlier ones (callIds are provider-unique per call, so
     * replacement only occurs on caller misuse — last-writer-wins keeps the
     * map bounded).
     */
    public void register(String callId, Process process, AtomicReference<StringBuilder> capturedRef,
                         Runnable cancelAction) {
        if (callId == null || process == null) {
            return;
        }
        inFlight.put(callId, new InFlight(process, capturedRef, cancelAction));
    }

    public void unregister(String callId) {
        if (callId != null) {
            inFlight.remove(callId);
        }
    }

    /**
     * Snapshot of the registered execution's incremental output. Returns
     * {@link BashProgressSnapshot#empty()} for unknown/finished callIds —
     * an explicit value, not null.
     */
    public BashProgressSnapshot progressSnapshot(String callId) {
        InFlight execution = callId == null ? null : inFlight.get(callId);
        if (execution == null) {
            return BashProgressSnapshot.empty();
        }
        StringBuilder captured = execution.capturedRef.get();
        if (captured == null) {
            return new BashProgressSnapshot("", 0L, true);
        }
        String full = captured.toString();
        String tail = full.length() <= TAIL_CHARS ? full : full.substring(full.length() - TAIL_CHARS);
        return new BashProgressSnapshot(tail, full.length(), true);
    }

    /**
     * Cancels the registered execution: destroys the process (and runs the
     * extra cancellation hook, e.g. {@code docker kill}). Returns false when
     * the callId has no in-flight registration.
     *
     * <p>3.2-C6-009（plan 2557 裁定 F）：销毁段按 {@code HostBashSandbox.killTree}
     * 同款 UOE/SecurityException 守卫包裹；销毁失败（根进程 destroyForcibly 抛出）
     * 时注册项 {@code putIfAbsent} 回填保留 + cancel-pending 标记 + rethrow——
     * boolean 外部契约保持（失败以异常表达，≠「无在途」），二次 cancel 可重试；
     * remove 顺序外部契约不变。
     */
    public boolean cancelExecution(String callId) {
        InFlight execution = callId == null ? null : inFlight.remove(callId);
        if (execution == null) {
            return false;
        }
        execution.cancelPending = true;
        try {
            // killTree 同款守卫：descendants 逐个 best-effort（单个句柄不支持/被安全
            // 策略拒绝不放大）；整个 descendants 流抛 UOE/SecurityException 也降级不阻断
            try {
                execution.process.descendants().forEach(ProcessHandle::destroyForcibly);
            } catch (UnsupportedOperationException | SecurityException degraded) {
                // ProcessHandle API unsupported / denied — root kill below still attempted
            }
            try {
                execution.process.destroyForcibly();
            } catch (UnsupportedOperationException | SecurityException destroyFailed) {
                // 销毁失败：回填注册项（失败≠「无在途」，二次 cancel 可重试）+ rethrow
                inFlight.putIfAbsent(callId, execution);
                throw destroyFailed;
            }
        } catch (RuntimeException e) {
            // 兜底：任何销毁段异常同样回填（保持「失败≠无在途」不变式）
            inFlight.putIfAbsent(callId, execution);
            throw e;
        }
        // C6-001 (MA-02, plan 2501 Phase 2): reader-exit compensation on the cancel
        // path — closing the pipes forces the (interrupt-insensitive) reader thread's
        // blocking read to abort even when a detached twin process outlives the kill.
        // 守卫降级路径下补偿仍执行（销毁段被吞的失败不跳过 safeClose）。
        IoHelper.safeClose(execution.process.getInputStream());
        IoHelper.safeClose(execution.process.getErrorStream());
        if (execution.cancelAction != null) {
            try {
                execution.cancelAction.run();
            } catch (RuntimeException e) {
                // cancellation must surface its failure, not silently drop it
                throw e;
            }
        }
        return true;
    }
}
