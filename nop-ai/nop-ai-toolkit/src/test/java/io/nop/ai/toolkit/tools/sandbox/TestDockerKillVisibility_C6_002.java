package io.nop.ai.toolkit.tools.sandbox;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 3.2-C6-002（plan 2501 Phase 2，MA-03 docker 清理族主登记）kill 失败可见性断言
 * （C10-006 形态，本类的红载体与修复前后均可编译——反射调用既有私有站点）：
 * <p>
 * 缺陷：{@code killContainer} 两步清理全部 {@code catch Exception ignored}——kill/rm
 * 失败（binary 缺失 / daemon 瞬断 / 非零退出）无痕迹无升级；finally unregister 撤销
 * 唯一取消句柄后容器成为不可寻址永久残留（容器名是唯一可寻址面）。
 * <p>
 * 修复面：kill/rm 失败（异常或非零退出）显式 WARN（容器名 + 步骤可见）。容器侧兜底
 * 三选一裁定 = <b>声明运维边界 + owner doc 包络</b>（WARN 的容器名即清扫包络；
 * {@code --stop-timeout} 需 daemon 配合不解决 rm 失败面、后台 reaper 归 plan
 * Deferred「容器侧兜底增强」）。超时路径 CLI destroyForcibly 兜底的接线验证见
 * {@code TestDockerTimeoutCliBackstop_C6_002}（C10-006 共享接缝，MA-03/MA-04 一缝两单）。
 */
public class TestDockerKillVisibility_C6_002 {

    /**
     * kill 失败可见性断言：killContainer 执行注入失败的清理步骤（本机 docker CLI 对
     * 不存在容器的 {@code docker kill} 以非零退出失败 / binary 缺失时以异常失败——两种
     * 失败形态都注入）时必须留下 WARN 痕迹（容器名 + 步骤可见）。修复前双
     * {@code catch ignored} 静默吞、无任何日志（先红）。
     * <p>
     * 注记：rm 步骤不断言 WARN——docker CLI 对「删除不存在的容器」语义为成功（exit 0，
     * 幂等清理），不构成失败；rm 失败可见性与 kill 同构同 diff。
     */
    @Test
    @Timeout(30)
    public void killFailureIsVisibleNotSwallowed() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(DockerBashSandbox.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Method killContainer = DockerBashSandbox.class.getDeclaredMethod("killContainer", String.class);
            killContainer.setAccessible(true);
            killContainer.invoke(null, "c6-002-kill-fail-container");

            assertTrue(hasWarn(appender, "docker kill FAILED"),
                    "a failed `docker kill` must leave a WARN with the step visible");
            assertTrue(hasWarn(appender, "c6-002-kill-fail-container"),
                    "the WARN must carry the container name (the only addressability for cleanup)");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static boolean hasWarn(ListAppender<ILoggingEvent> appender, String needle) {
        return appender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains(needle));
    }
}
