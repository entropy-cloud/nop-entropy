package io.nop.stream.core.testsupport;

import java.util.function.BooleanSupplier;

/**
 * 测试等待工具：测试不依赖机器快慢——优先等待确定性条件（{@link #until}）；
 * 「目标线程已到达其阻塞点」用 {@link #untilThreadParked} / {@link #untilThreadSettles}
 * 的结构判据表达。超时上限为死锁保护，不承载语义。
 *
 * <p>时间确定化（plan 2510）：原 {@code elapsed}（纯时间流逝窗口）与
 * {@code staysTrue}（「X 毫秒内未发生」观察窗）属禁止形态，调用点清零后已从
 * canonical 实现删除（防静默复用）——时间语义场景一律 ManualClock 显式推进
 * 或结构判据替代，见 docs-for-ai/02-core-guides/testing.md「禁止依赖敏感时间等待」。
 */
public final class TestAwait {
    private TestAwait() {
    }

    /** 轮询等待确定性条件成立（默认 60s 死锁保护上限，轮询间隔 20ms）。 */
    public static void until(String what, BooleanSupplier cond) {
        until(what, cond, 60_000L);
    }

    public static void until(String what, BooleanSupplier cond, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!cond.getAsBoolean()) {
            if (System.currentTimeMillis() >= deadline)
                throw new AssertionError("Timed out after " + timeoutMs + "ms waiting for: " + what);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for: " + what, e);
            }
        }
    }

    /** 等待目标线程进入阻塞等待态（WAITING/TIMED_WAITING），即已到达其阻塞点。 */
    public static void untilThreadParked(String what, Thread t) {
        until(what + " (thread " + t.getName() + " parked)",
                () -> t.getState() == Thread.State.WAITING || t.getState() == Thread.State.TIMED_WAITING);
    }

    /** 等待目标线程到达稳态：进入阻塞等待（WAITING/TIMED_WAITING）或已结束。 */
    public static void untilThreadSettles(String what, Thread t) {
        until(what + " (thread " + t.getName() + " settled)",
                () -> !t.isAlive() || t.getState() == Thread.State.WAITING
                        || t.getState() == Thread.State.TIMED_WAITING);
    }
}
