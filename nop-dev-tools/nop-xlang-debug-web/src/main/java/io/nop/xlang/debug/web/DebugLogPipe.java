/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每会话日志攒批管道（D1 §3.5 evt.logs，plan 2472）：条数阈值立即 flush / 定时间隔
 * flush / 会话销毁前 flush（残余 entries 尽力送达，D2 §3.6 不可补齐限制不变——只保
 * 已入 buffer 的）。
 *
 * <p>归属与接线（plan 2472 审计 F2 修订）：pipe 由 {@link DebugSessionBinder} 按
 * sessionId 创建持有、不随 bridge 生命周期；bridge 持 pipe 做 enqueue，pipe 持
 * bridge（sink）做 flush 出站；rebind 路径显式 {@link #attachSink} 重接。
 *
 * <p>并发纪律：pipe 锁只覆盖 buffer/排程/销毁状态；出站在锁外经 sink（bridge 的
 * session 出站锁）——flush 由入队线程/定时器线程/销毁线程三方触发，锁内 swap 摘除
 * buffer、锁外发送，不持 pipe 锁做 IO。swap 后入队的新 entry 归下一代 buffer
 * （destroyFlush 场景下的此类丢失为尽力送达边界，非缺陷）。
 */
public class DebugLogPipe {
    static final Logger LOG = LoggerFactory.getLogger(DebugLogPipe.class);

    private final int batchSize;
    private final long flushIntervalMillis;
    private final ScheduledExecutorService timer;

    private final AtomicBoolean destroyed = new AtomicBoolean();
    private final Object lock = this;
    private List<Map<String, Object>> buffer = new ArrayList<>();
    private ScheduledFuture<?> scheduledFlush;
    private volatile GatewayNotifierBridge sink;

    public DebugLogPipe(GatewayNotifierBridge sink, int batchSize, long flushIntervalMillis,
                        ScheduledExecutorService timer) {
        this.sink = sink;
        this.batchSize = Math.max(1, batchSize);
        this.flushIntervalMillis = Math.max(0, flushIntervalMillis);
        this.timer = timer;
    }

    /** rebind 重接出站 sink（旧 sink 的残余由调用方先 flush 尽力送达）。 */
    public void attachSink(GatewayNotifierBridge newSink) {
        synchronized (lock) {
            this.sink = newSink;
        }
    }

    /**
     * 入队一条 evt.log entry（bridge notifyLog/notifyWarn 路由至此）。条数达阈值立即
     * flush，否则排程定时 flush。destroyed 后丢弃（尽力送达边界）。
     */
    public void enqueue(Map<String, Object> entry) {
        List<Map<String, Object>> toFlush = null;
        synchronized (lock) {
            if (destroyed.get())
                return;
            buffer.add(entry);
            if (buffer.size() >= batchSize) {
                toFlush = swapBufferLocked();
            } else if (scheduledFlush == null && flushIntervalMillis > 0 && timer != null) {
                scheduledFlush = timer.schedule(this::flush, flushIntervalMillis, TimeUnit.MILLISECONDS);
            }
        }
        if (toFlush != null)
            send(toFlush);
    }

    /** 定时 flush 入口（timer 线程）。 */
    public void flush() {
        List<Map<String, Object>> toFlush;
        synchronized (lock) {
            toFlush = swapBufferLocked();
        }
        send(toFlush);
    }

    /**
     * 会话销毁 flush：取消排程任务、置 destroyed 位（后续 enqueue 丢弃）、残余 entries
     * 锁外尽力送达。由 binder 在 onDestroyed 回调（含 suppress 过渡会话路径）中
     * 先于 evt.sessionClosed 调用——保 evt.logs 先于终态事件的到达顺序。
     */
    public void destroyFlush() {
        List<Map<String, Object>> toFlush;
        synchronized (lock) {
            if (scheduledFlush != null) {
                scheduledFlush.cancel(false);
                scheduledFlush = null;
            }
            destroyed.set(true);
            toFlush = swapBufferLocked();
        }
        send(toFlush);
    }

    // 必须持 pipe 锁调用：摘除当前 buffer（swap），调用方锁外发送
    private List<Map<String, Object>> swapBufferLocked() {
        if (scheduledFlush != null) {
            scheduledFlush.cancel(false);
            scheduledFlush = null;
        }
        List<Map<String, Object>> drained = buffer;
        buffer = new ArrayList<>();
        return drained.isEmpty() ? null : drained;
    }

    private void send(List<Map<String, Object>> entries) {
        if (entries == null)
            return;
        GatewayNotifierBridge target = sink;
        if (target == null)
            return;
        try {
            target.sendLogs(entries);
        } catch (Exception e) {
            // 发送失败：连接已死或桥接异常，日志丢弃（尽力送达边界）
            LOG.debug("nop.xlang.debug-web.log-flush-fail:size={}", entries.size(), e);
        }
    }
}
