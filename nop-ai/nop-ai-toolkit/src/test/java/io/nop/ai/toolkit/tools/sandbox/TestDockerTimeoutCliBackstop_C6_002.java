package io.nop.ai.toolkit.tools.sandbox;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CRA 3.2-C6-002（plan 2501 Phase 2）超时路径接线验证（rule 23，C10-006 共享
 * {@code startDockerCli} 注入接缝）：真实 execute 超时链（fake CLI = 挂起的
 * {@code docker run} 代理）上断言 (a) 超时结果返回 (b) CLI 进程被 destroyForcibly
 * 终结（进程退出可观测——修复前 CLI 滞留）(c) kill/rm 失败 WARN 可观测（修复前静默）
 * (d) docker reader 线程预算内终止（MA-02 Docker 面 F3 补齐的运行时证据）。
 * 必红经变异论证登记（去除兜底/去 WARN 分别使 (b)/(c) 红——接缝与修复同 diff，
 * 修复前本类不编译，红载体见 {@code TestDockerKillVisibility_C6_002}）。
 */
public class TestDockerTimeoutCliBackstop_C6_002 {

    @TempDir
    File tempDir;

    private static void assumeShAvailable() {
        try {
            new ProcessBuilder("sh", "-c", "true").start().waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("sh not available");
        }
    }

    @Test
    @Timeout(30)
    public void timeoutPathDestroysCliProcessAndLogsKillFailure() throws Exception {
        assumeShAvailable();
        AtomicReference<Process> fakeCli = new AtomicReference<>();
        DockerBashSandbox sandbox = new DockerBashSandbox(
                "alpine:3.19", BashSandboxConfig.builder().wallSeconds(2).build(), List.of(tempDir.toPath())) {
            @Override
            Process startDockerCli(ProcessBuilder pb) {
                // fake `docker run`: a CLI proxy that hangs (container never exits);
                // mirror the production stdin=NUL redirect on the fake process
                File nul = new File(File.separatorChar == '\\' ? "NUL" : "/dev/null");
                Process real;
                try {
                    real = new ProcessBuilder("/bin/sh", "-c", "echo container-started; sleep 60")
                            .redirectErrorStream(true)
                            .redirectInput(nul)
                            .start();
                } catch (IOException e) {
                    throw io.nop.api.core.exceptions.NopException.adapt(e);
                }
                fakeCli.set(real);
                return real;
            }
        };

        Logger logger = (Logger) LoggerFactory.getLogger(DockerBashSandbox.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            BashSandboxResult result = sandbox.execute(BashSandboxRequest.builder()
                    .command(List.of("echo", "hi"))
                    .workingDirectory(tempDir)
                    // request-level config is the effective envelope (the request builder
                    // defaults to BashSandboxConfig.defaults() = 60s, overriding the
                    // sandbox-level defaultConfig)
                    .config(BashSandboxConfig.builder().wallSeconds(2).build())
                    .callId("c6-002-timeout")
                    .build());

            assertTrue(result.isTimedOut(), "wall timeout must be enforced");
            assertTrue(fakeCli.get() != null && !fakeCli.get().isAlive(),
                    "the local `docker run` CLI process must be destroyed by the timeout-path backstop");
            boolean killFailureVisible = appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN
                    && e.getFormattedMessage().contains("docker kill FAILED"));
            assertTrue(killFailureVisible,
                    "cleanup failure must be visible on the real timeout path");
            assertTrue(awaitReaderThreadsGone("nop-bash-sandbox-docker-reader-", 8000),
                    "docker reader thread must terminate after the timeout path");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
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
}
