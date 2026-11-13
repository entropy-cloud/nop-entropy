package io.nop.http.client.jdk;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.IoHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 响应体阶段 idle 读看门狗（plan 2505 / 4.3-C3-002 + 4.3-CL-026 + 4.3-C6-004 面③）。
 *
 * <p>JDK 客户端的 body 阶段读取（{@code in.read}/{@code readLine}）没有 socket 级
 * SoTimeout 可用，服务端中途停滞时读取线程无限阻塞。本看门狗按 idle 预算巡检读进度，
 * 超阈（或取消令牌已触发）时从独立线程关闭底层流，使阻塞中的 {@code read} 抛
 * {@link java.io.IOException} 进入既有错误路径——阻塞点有界解除。
 *
 * <p><b>承载约束（MA-16 收窄后口径）</b>：巡检任务复用 {@code GlobalExecutors.globalTimer()}
 * 调度，任务体仅「进度/取消判定 + 关闭流」短动作，不属于禁止的「长阻塞任务体直连
 * globalTimer」形态（设计文档 §3.3 watchdog 承载节）。
 *
 * <p>进度判定基准 = 最近一次成功 read 的墙钟时刻（{@link CoreMetrics#currentTimeMillis()}）。
 * tick = max(100ms, idle/10)，以极小检测延迟代价换取零每行调度 churn（设计文档 §四 被拒方案）。
 */
final class BodyIdleWatchdog {
    static final Logger LOG = LoggerFactory.getLogger(BodyIdleWatchdog.class);

    private final InputStream in;
    private final ICancelToken cancelToken;
    private final long idleTimeoutMillis;
    private final Future<?> task;
    private volatile long lastProgress;

    private BodyIdleWatchdog(InputStream in, ICancelToken cancelToken, long idleTimeoutMillis) {
        this.in = in;
        this.cancelToken = cancelToken;
        this.idleTimeoutMillis = idleTimeoutMillis;
        this.lastProgress = CoreMetrics.currentTimeMillis();
        long tick = Math.max(100L, idleTimeoutMillis / 10);
        this.task = GlobalExecutors.globalTimer().scheduleWithFixedDelay(this::check, tick, tick,
                TimeUnit.MILLISECONDS);
    }

    /**
     * 启动看门狗；{@code idleTimeoutMillis <= 0} 表示关闭看门狗（返回 null，调用点判空跳过）。
     */
    static BodyIdleWatchdog start(InputStream in, ICancelToken cancelToken, long idleTimeoutMillis) {
        if (idleTimeoutMillis <= 0)
            return null;
        return new BodyIdleWatchdog(in, cancelToken, idleTimeoutMillis);
    }

    /** 读取推进：每成功 read 一次调用，重置 idle 计时基准。 */
    void progress() {
        lastProgress = CoreMetrics.currentTimeMillis();
    }

    private void check() {
        long now = CoreMetrics.currentTimeMillis();
        boolean cancelled = cancelToken != null && cancelToken.isCancelled();
        if (cancelled || now - lastProgress > idleTimeoutMillis) {
            LOG.info("nop.http.body-idle-close:idleTimeout={},cancelled={},idle={}ms",
                    idleTimeoutMillis, cancelled, now - lastProgress);
            // 关闭底层流解除阻塞中的 read/readLine（IOException 进入既有错误路径）
            IoHelper.safeClose(in);
        }
    }

    /** 终态清理：停止巡检任务（不关闭流——流由调用方 TWR/清理路径负责）。 */
    void stop() {
        task.cancel(false);
    }
}
