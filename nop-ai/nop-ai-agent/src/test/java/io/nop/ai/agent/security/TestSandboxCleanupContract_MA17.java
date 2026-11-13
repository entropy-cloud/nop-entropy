package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 3.1-MA-17（plan 2501 Phase 4）双实现分叉收敛的共享清理契约护栏：
 * {@link NoOpSandboxBackend} 与 {@link DockerSandboxBackend} 的进程树收割 /
 * reader 退出补偿 / outputIncomplete 标记全部经 {@link SandboxProcessCleanup}
 * 单一实现承载——本类直接覆盖共享实现（共同契约测试），并经 NoOp 真实进程链做
 * 接线验证（rule 23）。
 *
 * <p><b>E2 环境注记（沿 plan 2501 Phase 2 口径）</b>：本 JDK 的进程退出 reaper
 * 会在主进程终止时强制关闭 stdio 管道流（{@code ProcessPipeInputStream.processExited}），
 * 「孙进程持管道 → reader 永久泄漏」在本环境被结构性缓解——reader 终止类断言在
 * 修复前基线亦可能绿；关流补偿为防御性在位面（部分 JDK 上关闭时序与 read 阻塞
 * 竞态确实不能唤醒读者），变异论证承载必红证据（去除关流/收割分支分别红）。
 */
public class TestSandboxCleanupContract_MA17 {

    private static void assumeShAvailable() {
        try {
            new ProcessBuilder("sh", "-c", "true").start().waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("sh not available");
        }
    }

    private static long readerThreadCount(String namePrefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().startsWith(namePrefix))
                .count();
    }

    private static boolean awaitReaderThreadsGone(String namePrefix, long budgetMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + budgetMillis;
        while (readerThreadCount(namePrefix) > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        return readerThreadCount(namePrefix) == 0;
    }

    /**
     * reapTree 收割断言（变异红载体：去除收割体则存活子进程断言红）：对存活进程
     * 树执行 {@code reapTree} 后根进程必须预算内死亡（超时路径 CLI 兜底与 success
     * 路径孙进程收割共用的同一机制）。
     */
    @Test
    @Timeout(30)
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void reapTreeTerminatesLiveProcessTree_MA17() throws Exception {
        assumeShAvailable();
        Process p = new ProcessBuilder("/bin/sh", "-c", "sleep 60").redirectErrorStream(true).start();
        assertTrue(p.isAlive(), "setup: the probe process must be alive before reap");

        SandboxProcessCleanup.reapTree(p);

        assertTrue(p.waitFor(5, TimeUnit.SECONDS),
                "reapTree must destroy the root process (destroyForcibly backstop)");
    }

    /**
     * reader 退出补偿断言：子进程静默存活（无 EOF、无退出——JDK 退出 reaper 不
     * 触发）时 reader 阻塞在原生 read；{@code awaitReaderExit} 必须在预算内使
     * reader 终止（关流补偿替代无效 interrupt）。修复前 interrupt-only 形态下
     * reader 永久滞留（变异红载体：去除关流分支则本断言红）。
     */
    @Test
    @Timeout(30)
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void awaitReaderExitCompensatesByClosingStream_MA17() throws Exception {
        assumeShAvailable();
        Process p = new ProcessBuilder("/bin/sh", "-c", "sleep 60")
                .redirectErrorStream(true).start();
        AtomicReference<StringBuilder> ref = new AtomicReference<>();
        Thread reader = new Thread(() -> SandboxProcessCleanup.drainStream(
                p.getInputStream(), 65536, ref), "ma17-compensation-reader");
        reader.setDaemon(true);
        reader.start();
        Thread.sleep(200); // let the reader block in read()

        boolean settled = SandboxProcessCleanup.awaitReaderExit(p, reader, 1);
        p.destroyForcibly();

        assertTrue(settled,
                "close-stream compensation must release the pipe-blocked reader within the "
                        + "bounded budget (interrupt alone cannot break a native pipe read)");
    }

    /**
     * NoOp success 路径孙进程持管道 E2E（3.1-C10-011 共享层形态）：{@code cmd &}
     * 孙进程持有 stdout 写端时 execute 必须有界返回、输出完整交付、reader 预算内
     * 终止（不泄漏线程）；settled reader ⇒ {@code outputIncomplete=false}。
     */
    @Test
    @Timeout(60)
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void noopSuccessPathWithGrandchildPipeReturnsBounded_MA17() throws Exception {
        assumeShAvailable();
        NoOpSandboxBackend backend = new NoOpSandboxBackend();
        SandboxRequest req = SandboxRequest.builder()
                .command(List.of("sh", "-c", "echo main-output; sleep 30 &"))
                .config(SandboxConfig.builder().wallSeconds(1).build())
                .build();

        long start = System.currentTimeMillis();
        SandboxResult result = backend.execute(req);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(0, result.getExitCode(), "main process exited 0");
        assertTrue(result.getStdout().contains("main-output"),
                "output captured: " + result.getStdout());
        assertFalse(result.isTimedOut(), "natural completion is not a timeout");
        assertFalse(result.isOutputIncomplete(),
                "a settled reader means complete capture — no incomplete marker");
        assertTrue(elapsed < 10_000L,
                "success path must return bounded, not wait on the detached twin; elapsed="
                        + elapsed + "ms");
        assertTrue(awaitReaderThreadsGone("nop-sandbox-stdout-reader", 8000),
                "reader thread must terminate after execute returns (pipe-close compensation; "
                        + "the sleep-30 grandchild holds the stdout write end)");
    }

    /**
     * NoOp timeout 路径回归钉：killTree 收割后 result 携带 {@code timedOut=true}，
     * reader 收敛且 {@code outputIncomplete=false}（killTree 关闭管道 → reader
     * 预算内终止——超时路径的输出不丢 outputIncomplete 语义）。
     */
    @Test
    @Timeout(60)
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void noopTimeoutPathMarksTimedOutAndSettlesReader_MA17() {
        NoOpSandboxBackend backend = new NoOpSandboxBackend();
        SandboxRequest req = SandboxRequest.builder()
                .command(List.of("sh", "-c", "echo partial; sleep 30"))
                .config(SandboxConfig.builder().wallSeconds(1).build())
                .build();

        SandboxResult result = backend.execute(req);

        assertTrue(result.isTimedOut(), "wall budget exceeded → timedOut");
        assertTrue(result.getStdout().contains("partial"),
                "partial output before the kill is still captured: " + result.getStdout());
        assertFalse(result.isOutputIncomplete(),
                "killTree closes the pipe so the reader settles and the capture is final");
    }

    /**
     * {@code outputIncomplete} 标记契约（MA-17 新增终态面）：六参构造透传、五参
     * 构造缺省 false、toString 可观测——「部分/空输出静默当完整交付」面在结果
     * 载体层闭合。
     */
    @Test
    void sandboxResultOutputIncompleteMarkerContract_MA17() {
        SandboxResult marked = new SandboxResult(0, "partial", "", 1L, false, true);
        assertTrue(marked.isOutputIncomplete(), "explicit true must be carried");
        assertTrue(marked.toString().contains("outputIncomplete=true"),
                "the marker must be observable in toString: " + marked);

        SandboxResult legacy = new SandboxResult(0, "out", "", 1L, false);
        assertFalse(legacy.isOutputIncomplete(), "legacy constructor defaults to false");
        assertFalse(SandboxResult.success(0, "out", "", 1L).isOutputIncomplete(),
                "success factory defaults to false");
    }
}
