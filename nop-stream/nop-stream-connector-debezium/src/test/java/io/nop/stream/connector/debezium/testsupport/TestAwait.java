package io.nop.stream.connector.debezium.testsupport;

import java.util.function.BooleanSupplier;

/**
 * 测试等待工具：测试不依赖机器快慢——优先等待确定性条件（{@link #until}），
 * 时间语义必须流逝的场景用 {@link #elapsed} 循环形式；超时上限为死锁保护。
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

    /** 时间语义窗口：循环等待 elapsed 毫秒流逝（10ms 片），不依赖单次固定 sleep。 */
    public static void elapsed(String what, long ms) {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(Math.min(10, Math.max(1, deadline - System.currentTimeMillis())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for: " + what, e);
            }
        }
    }

    /** 负向窗口：在 windowMs 内持续断言不变量成立（每 10ms 检查一次）。 */
    public static void staysTrue(String what, BooleanSupplier invariant, long windowMs) {
        long deadline = System.currentTimeMillis() + windowMs;
        while (true) {
            if (!invariant.getAsBoolean())
                throw new AssertionError("Invariant violated while waiting for: " + what);
            if (System.currentTimeMillis() >= deadline)
                return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for: " + what, e);
            }
        }
    }
}
