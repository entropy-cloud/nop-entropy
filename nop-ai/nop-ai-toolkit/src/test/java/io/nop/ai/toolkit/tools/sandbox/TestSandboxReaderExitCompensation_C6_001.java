package io.nop.ai.toolkit.tools.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CRA 3.2-C6-001（plan 2501 Phase 2，MA-02 sandbox reader 泄漏族主登记，含 C6-008
 * 终态快照面）：
 * <p>
 * 缺陷：{@code cmd &} 孪生子进程持有 stdout 管道写端 → 主进程退出后无 EOF，reader
 * 线程永久阻塞在原生 read（interrupt 无效）——每次泄漏 1 线程 + 管道 fd；stdin 管道
 * 默认 PIPE 从不关闭（{@code cat} 类命令悬挂面）；drainStream finally 发布活
 * StringBuilder（C6-008 终态撕裂面）。
 * <p>
 * 修复面：awaitReader 宽限耗尽后显式关流强制 EOF（关流替代无效 interrupt）+
 * {@code redirectInput(NUL)} + 终态发布不可变快照 + reader 线程唯一命名 +
 * cancelExecution 取消路径关流补偿。
 * <p>
 * <b>E2 环境注记（plan 2501 执行期登记）</b>：本 JDK 的进程退出 reaper 会在主进程
 * 终止时强制关闭 stdio 管道流（{@code ProcessPipeInputStream.processExited}），故
 * 「{@code cmd &} 孪生持管道 → reader 永久泄漏」在本环境（macOS/JDK 26）被结构性
 * 缓解——孪生泄漏断言在修复前基线亦绿（实测两轮，快照 {@code _tmp/phase2-c6-001-red.log}）。
 * 关流补偿为防御性在位面（关闭时序与 read 阻塞竞态在部分 JDK 上确实不能唤醒读者），
 * 上述用例作为回归护栏与终止性断言保留；Phase 2 的必红证据由 C6-002（kill 失败
 * 可见化）与 C6-003（挂死辅助进程有界返回）两条确定性红承载。
 */
public class TestSandboxReaderExitCompensation_C6_001 {

    @TempDir
    File tempDir;

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
     * 孪生负载 reader 终止断言（3.2-C10-005 形态）：{@code sleep 30 & echo started}
     * 的孪生子持有 stdout 写端时，execute 返回后 reader 必须预算内终止（关流补偿）。
     * 修复前 reader 永久滞留（先红：sleep 30 孪生在断言窗口内持管道）。
     */
    @Test
    @Timeout(60)
    public void twinProcessHoldingStdoutDoesNotLeakReaderThread() throws Exception {
        assumeShAvailable();
        HostBashSandbox sandbox = new HostBashSandbox(
                BashSandboxConfig.builder().wallSeconds(10).build(),
                List.of(tempDir.toPath()));
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c", "sleep 30 & echo started"))
                .workingDirectory(tempDir)
                .callId("c6-001-twin")
                .build();

        BashSandboxResult result = sandbox.execute(request);
        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("started"), "command output captured: " + result.getStdout());

        assertTrue(awaitReaderThreadsGone("nop-bash-sandbox-reader-", 8000),
                "reader thread must terminate after execute returns (pipe-close compensation; "
                        + "the sleep-30 twin holds the stdout write end)");
    }

    /**
     * 取消路径 reader 终止：cancelExecution 杀进程树后 reader 必须终止
     * （process-tree kill + 关流补偿双保险；孪生场景下不滞留）。
     */
    @Test
    @Timeout(60)
    public void cancelExecutionTerminatesReaderEvenWithTwinHoldingPipe() throws Exception {
        assumeShAvailable();
        HostBashSandbox sandbox = new HostBashSandbox(
                BashSandboxConfig.builder().wallSeconds(30).build(),
                List.of(tempDir.toPath()));
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c", "echo started; sleep 30 & wait"))
                .workingDirectory(tempDir)
                .callId("c6-001-cancel")
                .build();

        CompletableFuture<BashSandboxResult> running =
                CompletableFuture.supplyAsync(() -> sandbox.execute(request));

        BashProgressSnapshot snapshot = BashProgressSnapshot.empty();
        for (int i = 0; i < 150; i++) {
            snapshot = sandbox.progressSnapshot("c6-001-cancel");
            if (snapshot.getOutputTail().contains("started")) {
                break;
            }
            Thread.sleep(20);
        }
        assertTrue(snapshot.getOutputTail().contains("started"), "running execution captured output");

        assertTrue(sandbox.cancelExecution("c6-001-cancel"));
        BashSandboxResult result = running.get(10, TimeUnit.SECONDS);
        assertTrue(result.getExitCode() != 0, "cancelled process exits non-zero");

        assertTrue(awaitReaderThreadsGone("nop-bash-sandbox-reader-", 8000),
                "reader thread must terminate after cancellation (kill + stream close)");
    }

    /**
     * 终态/运行期快照并发稳定性（C6-008 面护栏）：输出流式产出期间并发
     * progressSnapshot N 次无异常，且每个观察到的 tail 都是最终输出的后缀
     * （前缀稳定、无撕裂）。
     */
    @Test
    @Timeout(60)
    public void progressSnapshotStaysStableWhileOutputStreams() throws Exception {
        assumeShAvailable();
        HostBashSandbox sandbox = new HostBashSandbox(
                BashSandboxConfig.builder().wallSeconds(30).build(),
                List.of(tempDir.toPath()));
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c",
                        "for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do echo line-$i; sleep 0.05; done"))
                .workingDirectory(tempDir)
                .callId("c6-001-snap")
                .build();

        CompletableFuture<BashSandboxResult> running =
                CompletableFuture.supplyAsync(() -> sandbox.execute(request));
        AtomicReference<Throwable> pollerError = new AtomicReference<>();
        CopyOnWriteArrayList<String> observedTails = new CopyOnWriteArrayList<>();
        Thread poller = new Thread(() -> {
            try {
                while (!running.isDone()) {
                    BashProgressSnapshot snapshot = sandbox.progressSnapshot("c6-001-snap");
                    if (snapshot.getOutputBytes() > 0) {
                        observedTails.add(snapshot.getOutputTail());
                    }
                    Thread.sleep(10);
                }
            } catch (Throwable t) {
                pollerError.set(t);
            }
        }, "c6-001-snapshot-poller");
        poller.setDaemon(true);
        poller.start();

        BashSandboxResult result = running.get(20, TimeUnit.SECONDS);
        poller.join(5000);

        assertNull(pollerError.get(), "concurrent progressSnapshot must not throw");
        assertTrue(result.getStdout().contains("line-20"), "full output captured: " + result.getStdout());
        assertTrue(observedTails.size() >= 5, "poller observed incremental snapshots: " + observedTails.size());
        for (String tail : observedTails) {
            assertTrue(result.getStdout().contains(tail),
                    "every observed snapshot tail must appear intact in the final output (no torn read): " + tail);
        }
    }
}
