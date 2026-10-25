package io.nop.ai.agent.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 3.1-MA-17（plan 2501 Phase 4）超时路径与 success 路径接线验证（rule 23，
 * {@code startDockerCli} 注入接缝——与 toolkit C10-006 共享接缝同型，MA-03/MA-17
 * 一缝两单）：真实 execute 链上断言清理契约运行时生效。接缝与修复同 diff，修复前
 * 本类不编译——必红由 {@code TestDockerKillVisibility_MA17} 两条确定性红与变异
 * 论证承载（去除 CLI 兜底 → fake CLI 滞留断言红；去 kill WARN → 可见化断言红）。
 *
 * <ul>
 *   <li><b>超时路径</b>：TIMEOUT 异常抛出 + 本地 CLI 进程被 destroyForcibly 终结
 *       （进程存活计数——修复前 CLI 滞留）+ kill 失败 WARN 可观测 + reader 线程
 *       预算内终止。</li>
 *   <li><b>success 路径</b>：结果携带 outputIncomplete 契约面（settled ⇒ false）、
 *       reader 预算内终止。</li>
 * </ul>
 */
public class TestDockerSandboxCleanup_MA17 {

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

    private static boolean hasWarn(ListAppender<ILoggingEvent> appender, String needle) {
        return appender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains(needle));
    }

    /**
     * 超时路径接线验证：fake CLI（挂起的 docker run 代理，daemon 不需要）上断言
     * (a) TIMEOUT 异常抛出 (b) 本地 CLI 进程被 destroyForcibly 终结 (c) kill 失败
     * WARN 可观测 (d) reader 线程预算内终止。
     */
    @Test
    @Timeout(60)
    public void timeoutPathDestroysCliAndLogsKillFailure_MA17() throws Exception {
        assumeShAvailable();
        AtomicReference<Process> fakeCli = new AtomicReference<>();
        DockerSandboxBackend backend = new DockerSandboxBackend(
                "alpine:3.19", SandboxConfig.builder().wallSeconds(1).build()) {
            @Override
            Process startDockerCli(ProcessBuilder pb) {
                // fake `docker run`: a CLI proxy that hangs (container never exits)
                try {
                    Process real = new ProcessBuilder("/bin/sh", "-c",
                            "echo container-started; sleep 60")
                            .redirectErrorStream(true)
                            .start();
                    fakeCli.set(real);
                    return real;
                } catch (Exception e) {
                    throw new SandboxException(SandboxFailureReason.DOCKER_UNAVAILABLE,
                            "fake CLI start failed: " + e.getMessage(), e);
                }
            }
        };

        Logger logger = (Logger) LoggerFactory.getLogger(DockerSandboxBackend.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            SandboxRequest req = SandboxRequest.builder()
                    .command(List.of("echo", "hi"))
                    .config(SandboxConfig.builder().wallSeconds(1).build())
                    .build();

            SandboxException ex = assertThrows(SandboxException.class, () -> backend.execute(req),
                    "the wall-time budget must fire and surface as TIMEOUT");
            assertEquals(SandboxFailureReason.TIMEOUT, ex.getReason(), "timeout reason surfaced");

            // destroyForcibly is asynchronous — bounded await for the CLI's death
            long deadline = System.currentTimeMillis() + 5000;
            while (fakeCli.get() != null && fakeCli.get().isAlive()
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(fakeCli.get() != null && !fakeCli.get().isAlive(),
                    "the local `docker run` CLI process must be destroyed by the timeout-path "
                            + "backstop (mutation: removing the reap leaves the CLI behind)");
            assertTrue(hasWarn(appender, "docker kill FAILED"),
                    "cleanup failure must be visible on the real timeout path");
            assertTrue(awaitReaderThreadsGone("nop-docker-sandbox-reader", 8000),
                    "docker reader thread must terminate after the timeout path");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    /**
     * success 路径 reader 收敛 + outputIncomplete 契约（fake CLI 主进程正常退出）：
     * 结果携带 exit 0 与完整捕获；reader 必须预算内终止；未滞留时
     * {@code outputIncomplete=false}（settled ⇒ 完整捕获，见
     * {@link SandboxResult} javadoc）。
     */
    @Test
    @Timeout(60)
    public void successPathSettlesReaderAndCarriesMarkerContract_MA17() throws Exception {
        assumeShAvailable();
        DockerSandboxBackend backend = new DockerSandboxBackend(
                "alpine:3.19", SandboxConfig.builder().wallSeconds(1).build()) {
            @Override
            Process startDockerCli(ProcessBuilder pb) {
                // fake CLI: exits 0 after emitting output
                try {
                    return new ProcessBuilder("/bin/sh", "-c", "echo fake-container-output")
                            .redirectErrorStream(true)
                            .start();
                } catch (Exception e) {
                    throw new SandboxException(SandboxFailureReason.DOCKER_UNAVAILABLE,
                            "fake CLI start failed: " + e.getMessage(), e);
                }
            }
        };

        SandboxRequest req = SandboxRequest.builder()
                .command(List.of("echo", "hi"))
                .config(SandboxConfig.builder().wallSeconds(5).build())
                .build();

        SandboxResult result = backend.execute(req);
        assertEquals(0, result.getExitCode(), "fake CLI exited 0");
        assertTrue(result.getStdout().contains("fake-container-output"),
                "output captured: " + result.getStdout());
        assertFalse(result.isOutputIncomplete(),
                "a settled reader means complete capture — no incomplete marker");
        assertFalse(result.isTimedOut(), "natural completion is not a timeout");
        assertTrue(awaitReaderThreadsGone("nop-docker-sandbox-reader", 8000),
                "reader thread must terminate on the success path");
    }
}
