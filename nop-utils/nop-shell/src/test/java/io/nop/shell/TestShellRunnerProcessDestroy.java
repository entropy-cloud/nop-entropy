/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.shell;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.env.PlatformEnv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static io.nop.shell.ShellErrors.ERR_SHELL_EXEC_COMMAND_FAIL;
import static io.nop.shell.ShellErrors.ERR_SHELL_EXEC_COMMAND_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C6-004（←C6-014）回归：ShellRunner.run 非 timeout 异常/中断/collector 抛错路径、
 * waitFor 超时子路径（时序 A/B/C/D）与 scheduleTimeout/collectError 调度拒绝窗（时序 E/F）
 * 均必须销毁已创建的子进程（外层 finally 兜底），且 safeDestroy 具备 destroyForcibly 升级
 * （destroy 后有界等待未退出则强杀）；stderr 读取线程随进程销毁有界收敛。
 * 对应设计裁定⑥：ai-dev/design/concurrency/plugin-lifecycle-and-config-source-hardening.md §3.6。
 */
public class TestShellRunnerProcessDestroy {

    // ==================== 可观测 stub Process ====================

    /**
     * 可注入 ShellRunner 的 stub Process：记录 destroy/destroyForcibly 调用计数，
     * 退出态可控（ignoreDestroy 模拟忽略 SIGTERM 的子进程，仅 destroyForcibly 能落定退出）。
     */
    static class ObservableProcess extends Process {
        final AtomicInteger destroyCount = new AtomicInteger();
        final AtomicInteger destroyForciblyCount = new AtomicInteger();
        final AtomicInteger exitCode = new AtomicInteger();
        final AtomicBoolean exited = new AtomicBoolean();
        final CountDownLatch noArgWaitEntered = new CountDownLatch(1);

        /**
         * 时序 D 专用：为 true 时 waitFor(timeout,unit) 不等待真实窗口，经过
         * earlyWaitReturnMillis 后即返回 false——模拟「diff 截止点早于 timer 截止点」，
         * 使内层 finally 能在 timer 触发前可靠 cancel，从而隔离外层兜底销毁路径。
         */
        volatile boolean earlyWaitReturnFalse;
        volatile long earlyWaitReturnMillis = 50L;

        /** 模拟忽略 SIGTERM 的子进程：destroy() 不落定退出，仅 destroyForcibly() 落定 */
        volatile boolean ignoreDestroy;

        private final InputStream stdout;
        private final InputStream stderr;
        private final OutputStream stdin;

        ObservableProcess(InputStream stdout, InputStream stderr, OutputStream stdin) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.stdin = stdin;
        }

        void exit(int code) {
            exitCode.set(code);
            exited.set(true);
        }

        @Override
        public OutputStream getOutputStream() {
            return stdin;
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() throws InterruptedException {
            noArgWaitEntered.countDown();
            while (!exited.get()) {
                // 对齐真实 Process.waitFor 的中断响应语义，供时序 B（中断）注入
                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedException("interrupted while waiting for process exit");
                LockSupport.parkNanos(1_000_000L);
            }
            return exitCode.get();
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (earlyWaitReturnFalse) {
                long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(earlyWaitReturnMillis);
                while (!exited.get() && System.nanoTime() < end)
                    Thread.sleep(1);
                return exited.get();
            }
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (!exited.get()) {
                if (System.nanoTime() >= deadline)
                    return false;
                Thread.sleep(1);
            }
            return true;
        }

        @Override
        public int exitValue() {
            if (!exited.get())
                throw new IllegalThreadStateException("process has not exited");
            return exitCode.get();
        }

        @Override
        public void destroy() {
            destroyCount.incrementAndGet();
            if (!ignoreDestroy)
                exit(137);
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCount.incrementAndGet();
            exit(137);
            return this;
        }
    }

    /** 捕获真实子进程句柄，供存活探针（isAlive）与测试侧清理使用 */
    static class ProcessCapturingRunner extends ShellRunner {
        volatile Process captured;

        @Override
        Process newProcess(ShellCommand command) throws IOException {
            Process process = super.newProcess(command);
            this.captured = process;
            return process;
        }
    }

    /** 注入 stub Process，并可模拟 scheduleTimeout/collectError 调度拒绝（时序 E/F） */
    static class StubShellRunner extends ShellRunner {
        final ObservableProcess proc;
        boolean rejectScheduleTimeout;
        boolean rejectCollectError;

        StubShellRunner(ObservableProcess proc) {
            this.proc = proc;
        }

        @Override
        Process newProcess(ShellCommand command) {
            return proc;
        }

        @Override
        Future<?> scheduleTimeout(ShellCommand command, Callable<?> cleanup) {
            if (rejectScheduleTimeout)
                throw new RejectedExecutionException("simulated globalTimer rejection");
            return super.scheduleTimeout(command, cleanup);
        }

        @Override
        Future<?> collectError(ShellCommand command, Process process, AtomicBoolean stopped,
                               IShellOutputCollector collector) {
            if (rejectCollectError)
                throw new RejectedExecutionException("simulated cachedThreadPool rejection");
            return super.collectError(command, process, stopped, collector);
        }
    }

    /** 模拟 stdin 断管：write 即抛 IOException */
    static OutputStream brokenPipeStdin() {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Broken pipe (simulated)");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("Broken pipe (simulated)");
            }
        };
    }

    static OutputStream sinkStdin() {
        return new OutputStream() {
            @Override
            public void write(int b) {
                // sink: discard all written bytes
            }
        };
    }

    static InputStream eofStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    static InputStream singleLineStream(String line) {
        return new ByteArrayInputStream((line + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /** stderr 读取线程钉住探针：read 阻塞直至流被 close，恢复后记录「读取线程已到达 EOF」 */
    static class PinnedErrorStream extends InputStream {
        private final CountDownLatch closeLatch = new CountDownLatch(1);
        private final CompletableFuture<Void> readerReachedEof = new CompletableFuture<>();

        CompletableFuture<Void> readerReachedEof() {
            return readerReachedEof;
        }

        @Override
        public int read() {
            // 模拟真实管道读的不可中断语义：Thread.interrupt 不能唤醒阻塞在 pipe read 上的线程，
            // 唯有流关闭（进程销毁 → EOF）才能释放。errorFuture.cancel 经 bindCancel 会 interrupt
            // 读取线程，若此处响应中断则桩失真（读取线程经 IOException 分支退出而非 EOF 收敛）
            boolean interrupted = false;
            try {
                while (true) {
                    try {
                        closeLatch.await();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted)
                    Thread.currentThread().interrupt();
            }
            // close 后到达 EOF：证明读取线程已从阻塞读中恢复并退出读取循环
            readerReachedEof.complete(null);
            return -1;
        }

        @Override
        public void close() {
            closeLatch.countDown();
        }
    }

    /** 在 stdout 行上抛错的 collector（时序 C：collector 抛错经 run 同步路径传播） */
    static final class ThrowingOnOutputCollector implements IShellOutputCollector {
        @Override
        public void onOutput(String line) {
            throw new IllegalStateException("collector fail on purpose, line=" + line);
        }

        @Override
        public void onError(String line) {
            // stub: this collector only fails on stdout lines
        }
    }

    // ==================== 测试辅助 ====================

    private static Throwable runInThread(ShellRunner runner, ShellCommand cmd, IShellOutputCollector collector)
            throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            try {
                runner.run(cmd, collector);
            } catch (Throwable t) {
                thrown.set(t);
            } finally {
                done.countDown();
            }
        }, "c6-004-run-probe");
        thread.setDaemon(true);
        thread.start();
        assertTrue(done.await(30, TimeUnit.SECONDS), "run() did not finish within bounded time");
        return thrown.get();
    }

    private static void assertFailCode(NopException e) {
        assertEquals(ERR_SHELL_EXEC_COMMAND_FAIL.getErrorCode(), e.getErrorCode());
    }

    // ==================== 时序 A：stdin 断管 IOException ====================

    @Test
    @Timeout(30)
    public void testSequenceAStdinBrokenPipeDestroysProcess() {
        ObservableProcess proc = new ObservableProcess(eofStream(), eofStream(), brokenPipeStdin());
        StubShellRunner runner = new StubShellRunner(proc);
        ShellCommand cmd = new ShellCommand("stub").input("x");

        NopException e = assertThrows(NopException.class, () -> runner.run(cmd, new DefaultShellOutputCollector()));
        assertFailCode(e);
        assertTrue(proc.destroyCount.get() >= 1, "process must be destroyed after stdin broken pipe, destroyCount="
                + proc.destroyCount.get());
        assertEquals(0, proc.destroyForciblyCount.get(), "normal destroy must terminate the stub, no forcible needed");
    }

    // ==================== 时序 B：中断 ====================

    @Test
    @Timeout(30)
    public void testSequenceBInterruptDestroysProcess() throws InterruptedException {
        ObservableProcess proc = new ObservableProcess(eofStream(), eofStream(), sinkStdin());
        StubShellRunner runner = new StubShellRunner(proc);
        ShellCommand cmd = new ShellCommand("stub");

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            try {
                runner.run(cmd, new DefaultShellOutputCollector());
            } catch (Throwable t) {
                thrown.set(t);
            } finally {
                done.countDown();
            }
        }, "c6-004-interrupt-probe");
        thread.setDaemon(true);
        thread.start();

        // 等 run 进入无参 waitFor 阻塞点再中断，保证中断发生在挂起窗口内
        assertTrue(proc.noArgWaitEntered.await(10, TimeUnit.SECONDS), "run did not reach waitFor");
        thread.interrupt();

        assertTrue(done.await(30, TimeUnit.SECONDS), "run did not finish within bounded time after interrupt");
        assertTrue(thrown.get() instanceof NopException, "interrupt must surface as NopException, got: " + thrown.get());
        assertTrue(proc.destroyCount.get() >= 1, "process must be destroyed after interrupt, destroyCount="
                + proc.destroyCount.get());
        assertEquals(0, proc.destroyForciblyCount.get());
    }

    // ==================== 时序 C：collector 抛错 ====================

    @Test
    @Timeout(30)
    public void testSequenceCCollectorThrowsDestroysProcess() {
        ObservableProcess proc = new ObservableProcess(singleLineStream("line1"), eofStream(), sinkStdin());
        StubShellRunner runner = new StubShellRunner(proc);
        ShellCommand cmd = new ShellCommand("stub").redirectErrorStream(true);

        NopException e = assertThrows(NopException.class, () -> runner.run(cmd, new ThrowingOnOutputCollector()));
        assertFailCode(e);
        assertTrue(proc.destroyCount.get() >= 1, "process must be destroyed after collector failure, destroyCount="
                + proc.destroyCount.get());
        assertEquals(0, proc.destroyForciblyCount.get());
    }

    // ==================== 时序 D：waitFor(diff) 超时子路径 ====================

    @Test
    @Timeout(30)
    public void testSequenceDWaitForTimeoutSubPathDestroysProcess() {
        ObservableProcess proc = new ObservableProcess(eofStream(), eofStream(), sinkStdin());
        // diff 截止点（50ms 处返回 false）早于 timer 截止点（1000ms），timer 在内层 finally 被可靠 cancel，
        // 销毁只能来自外层兜底——精确隔离时序 D 的漏杀点
        proc.earlyWaitReturnFalse = true;
        proc.earlyWaitReturnMillis = 50L;
        StubShellRunner runner = new StubShellRunner(proc);
        ShellCommand cmd = new ShellCommand("stub").timeout(1000);

        NopException e = assertThrows(NopException.class, () -> runner.run(cmd, new DefaultShellOutputCollector()));
        assertEquals(ERR_SHELL_EXEC_COMMAND_TIMEOUT.getErrorCode(), e.getErrorCode());
        assertEquals(1, proc.destroyCount.get(),
                "timeout sub-path must destroy the process exactly once via outer finally, destroyCount="
                        + proc.destroyCount.get());
        assertEquals(0, proc.destroyForciblyCount.get(), "normal destroy must terminate the stub");
    }

    // ==================== 时序 E：scheduleTimeout 调度拒绝 ====================

    @Test
    @Timeout(30)
    public void testSequenceEScheduleTimeoutRejectedDestroysProcess() {
        ObservableProcess proc = new ObservableProcess(eofStream(), eofStream(), sinkStdin());
        StubShellRunner runner = new StubShellRunner(proc);
        runner.rejectScheduleTimeout = true;
        ShellCommand cmd = new ShellCommand("stub");

        NopException e = assertThrows(NopException.class, () -> runner.run(cmd, new DefaultShellOutputCollector()));
        assertFailCode(e);
        assertEquals(1, proc.destroyCount.get(),
                "E window (rejection between newProcess and inner try) must be covered by outer finally, destroyCount="
                        + proc.destroyCount.get());
        assertEquals(0, proc.destroyForciblyCount.get());
    }

    // ==================== 时序 F：collectError 提交拒绝 ====================

    @Test
    @Timeout(30)
    public void testSequenceFCollectErrorRejectedDestroysProcess() {
        ObservableProcess proc = new ObservableProcess(eofStream(), eofStream(), sinkStdin());
        StubShellRunner runner = new StubShellRunner(proc);
        runner.rejectCollectError = true;
        ShellCommand cmd = new ShellCommand("stub");

        NopException e = assertThrows(NopException.class, () -> runner.run(cmd, new DefaultShellOutputCollector()));
        assertFailCode(e);
        assertEquals(1, proc.destroyCount.get(),
                "F window (rejection between newProcess and inner try) must be covered by outer finally, destroyCount="
                        + proc.destroyCount.get());
        assertEquals(0, proc.destroyForciblyCount.get());
    }

    // ==================== destroyForcibly 升级 ====================

    @Test
    @Timeout(30)
    public void testDestroyForciblyEscalationWhenDestroyIgnored() {
        ObservableProcess proc = new ObservableProcess(eofStream(), eofStream(), brokenPipeStdin());
        // 模拟忽略 SIGTERM 的子进程：destroy() 不退出，宽限等待耗尽后必须升级 destroyForcibly()
        proc.ignoreDestroy = true;
        StubShellRunner runner = new StubShellRunner(proc);
        ShellCommand cmd = new ShellCommand("stub").input("x");

        NopException e = assertThrows(NopException.class, () -> runner.run(cmd, new DefaultShellOutputCollector()));
        assertFailCode(e);
        assertEquals(1, proc.destroyCount.get());
        assertEquals(1, proc.destroyForciblyCount.get(),
                "destroyForcibly must be invoked after grace wait expires on a SIGTERM-ignoring process");
        assertTrue(proc.exited.get(), "stub must be terminated by destroyForcibly");
    }

    // ==================== stderr 读取线程有界收敛 ====================

    @Test
    @Timeout(30)
    public void testStderrReaderThreadConvergesAfterDestroy() throws Exception {
        PinnedErrorStream pinnedStderr = new PinnedErrorStream();
        ObservableProcess proc = new ObservableProcess(eofStream(), pinnedStderr, brokenPipeStdin());
        StubShellRunner runner = new StubShellRunner(proc);
        ShellCommand cmd = new ShellCommand("stub").input("x");

        try {
            NopException e = assertThrows(NopException.class, () -> runner.run(cmd, new DefaultShellOutputCollector()));
            assertFailCode(e);
            assertTrue(proc.destroyCount.get() >= 1);

            // 进程销毁 → error 流 close → readLine 到达 EOF → 读取任务退出（有界收敛，不再 pin 池线程）
            pinnedStderr.readerReachedEof().get(5, TimeUnit.SECONDS);
        } finally {
            // 兜底释放：防止修复前红阶段读取线程被永久钉住
            pinnedStderr.close();
        }
    }

    // ==================== 成功路径幂等无副作用（N6 平台守卫） ====================

    @Test
    @Timeout(30)
    public void testSuccessPathCommandStillReturnsCorrectResult() {
        // 真实子进程探针为 POSIX 面，沿用 testRunWithInput 的 assumeTrue 平台守卫先例
        org.junit.jupiter.api.Assumptions.assumeTrue(!PlatformEnv.isWindows());

        ShellCommand cmd = new ShellCommand("sh", "-c", "echo ok");
        DefaultShellOutputCollector collector = new DefaultShellOutputCollector();

        int exitCode = new ShellRunner().run(cmd, collector);
        assertEquals(0, exitCode);
        assertTrue(collector.getOutput().contains("ok"),
                "success path must be unaffected by the idempotent finally destroy, output=" + collector.getOutput());
    }

    // ==================== POSIX 真实进程探针：trap 忽略 SIGTERM 强杀 ====================

    @Test
    @Timeout(60)
    public void testTrapIgnoringRealProcessForceKilledAfterTimeout() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(!PlatformEnv.isWindows());

        ShellCommand cmd = new ShellCommand("sh", "-c", "trap \"\" TERM; sleep 30").timeout(500);
        ProcessCapturingRunner runner = new ProcessCapturingRunner();
        try {
            NopException e = assertThrows(NopException.class,
                    () -> runner.run(cmd, new DefaultShellOutputCollector()));
            assertEquals(ERR_SHELL_EXEC_COMMAND_TIMEOUT.getErrorCode(), e.getErrorCode());

            Process process = runner.captured;
            assertNotNull(process, "real subprocess must have been created");

            // 忽略 SIGTERM 的子进程在 destroyForcibly 升级后必须真实死亡（有界存活探针）
            long deadline = System.currentTimeMillis() + 10_000;
            while (process.isAlive() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertFalse(process.isAlive(), "trap-ignoring process must be force-killed by destroyForcibly escalation");
        } finally {
            // 测试侧兜底清理：修复前红阶段进程可能仍然存活
            if (runner.captured != null)
                runner.captured.destroyForcibly();
        }
    }
}
