package io.nop.ai.agent.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 3.1-MA-17（plan 2501 Phase 4，B1-04 回升 P1）Docker 清理链两条红载体
 * （3.2-C10-006 故障注入形态，与 toolkit TestDockerKillVisibility_C6_002 /
 * TestRunShortCommandBounded_C6_003 同套——反射直调既有私有站点，修复前后同
 * 签名可编译）：
 *
 * <ul>
 *   <li><b>kill 失败可见化</b>：{@code killContainer} 双 {@code catch ignored}
 *       静默吞改为显式 WARN（容器名 + 步骤可见）；修复前无任何日志（先红）。</li>
 *   <li><b>runShortCommand 有界化</b>：挂死辅助命令注入下预算内有界返回；修复前
 *       drain {@code in.read()} 永久阻塞（先红）。</li>
 * </ul>
 *
 * <p>超时路径 CLI destroyForcibly 兜底与 success 路径收敛的接线验证（rule 23，
 * {@code startDockerCli} 注入接缝）见 {@code TestDockerSandboxCleanup_MA17}
 * （接缝与修复同 diff，修复前不编译，必红由本类两条确定性红与变异论证承载）。
 */
public class TestDockerKillVisibility_MA17 {

    private static void assumeShAvailable() {
        try {
            new ProcessBuilder("sh", "-c", "true").start().waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("sh not available");
        }
    }

    private static boolean hasWarn(ListAppender<ILoggingEvent> appender, String needle) {
        return appender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains(needle));
    }

    /**
     * kill 失败可见化断言：killContainer 执行注入失败的清理步骤（本机 docker CLI
     * 对不存在容器的 {@code docker kill} 以非零退出失败 / binary 缺失时以异常失败
     * ——两种失败形态都注入）时必须留下 WARN 痕迹（容器名 + 步骤可见）。修复前双
     * {@code catch ignored} 静默吞、无任何日志（先红）。
     * <p>
     * 注记：rm 步骤不断言 WARN——docker CLI 对「删除不存在的容器」语义为成功
     * （exit 0，幂等清理），不构成失败；rm 失败可见性与 kill 同构同 diff。
     */
    @Test
    @Timeout(60)
    public void killFailureIsVisibleNotSwallowed_MA17() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(DockerSandboxBackend.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Method killContainer = DockerSandboxBackend.class
                    .getDeclaredMethod("killContainer", String.class);
            killContainer.setAccessible(true);
            killContainer.invoke(null, "ma17-kill-fail-container");

            assertTrue(hasWarn(appender, "docker kill FAILED"),
                    "a failed `docker kill` must leave a WARN with the step visible");
            assertTrue(hasWarn(appender, "ma17-kill-fail-container"),
                    "the WARN must carry the container name (the only addressability for cleanup)");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    /**
     * 挂死辅助进程注入有界返回断言：daemon 挂死形态（静默持管道不退出的 helper）
     * 下 runShortCommand 必须预算内有界返回，超时清理线程不再被钉死（wallSeconds
     * 契约不被绕过）。修复前 drain read 永久阻塞（先红）。
     */
    @Test
    @Timeout(60)
    public void wedgedSilentHelperCommandReturnsBounded_MA17() throws Exception {
        assumeShAvailable();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                Method runShort = DockerSandboxBackend.class
                        .getDeclaredMethod("runShortCommand", String[].class);
                runShort.setAccessible(true);
                runShort.invoke(null, (Object) new String[]{"/bin/sh", "-c", "sleep 60"});
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        }, "ma17-short-command-caller");
        caller.setDaemon(true);
        caller.start();

        assertTrue(done.await(20, TimeUnit.SECONDS),
                "runShortCommand must return within the short-command budget (pre-fix: the drain "
                        + "read blocks forever on the wedged helper holding the pipe)");
        assertNull(error.get(), "bounded return must not throw: " + error.get());
    }
}
