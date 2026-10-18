/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.graphql.core.ws.IWebSocketSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 每连接异步出站通道（plan 2479，item 24 审计 C-1 remediation）：有界队列 + 专职
 * daemon drain 线程。引擎回调线程 / keepalive 定时器 / 持 B·M 锁路径只做
 * {@link #offer}（非阻塞），WS IO 全部发生在本连接专属的 drain 线程上——
 * 停滞客户端只阻塞自己的 drain 线程，不持任何网关锁、不影响其他连接
 * （D2 §3.7 出站拓扑修订；R1 B-2 裁定：共享 WS 适配器不动，socket 级发送超时
 * 不在本方案内，半死 TCP 占用专属 drain 线程直至容器写超时，残余登记 D2）。
 *
 * <p>丢弃语义：队列满（256）丢弃该帧并按连接限频 warn（每 5s 窗口最多 1 条，
 * R1 O-3）——D1 §3.5 事件 best-effort 不重放；session 已 closed 的帧在 drain 时
 * 丢弃（B-1 裁定：onClose 不摘队列，断连后宽限路径事件仍可入队、drain 时按
 * isClosed 丢弃，队列生命周期终于 onSessionDestroyed 的 removeOutbound）。
 *
 * <p>drain 线程惰性启动（首次 offer），空闲 {@link #IDLE_TIMEOUT_MILLIS} 自退出，
 * 再次 offer 时重新启动——连接数有界则线程数有界。
 */
public class AsyncOutbound {
    static final Logger LOG = LoggerFactory.getLogger(AsyncOutbound.class);

    static final int QUEUE_CAPACITY = 256;
    static final long IDLE_TIMEOUT_MILLIS = 30_000;
    static final long WARN_INTERVAL_MILLIS = 5_000;

    private final IWebSocketSession session;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicInteger state = new AtomicInteger(ST_IDLE);
    private final AtomicLong lastWarnAt = new AtomicLong();

    private static final int ST_IDLE = 0;
    private static final int ST_RUNNING = 1;
    private static final int ST_CLOSED = 2;
    private static final int ST_DONE = 3;

    private volatile Thread drainThread;

    public AsyncOutbound(IWebSocketSession session) {
        this.session = session;
    }

    /** 非阻塞入队；满则丢帧 + 限频 warn。closed/done 后静默丢弃（连接消亡，事件 best-effort）。 */
    public boolean offer(String json) {
        if (state.get() >= ST_CLOSED)
            return false;
        if (!queue.offer(json)) {
            long now = System.currentTimeMillis();
            long last = lastWarnAt.get();
            if (now - last >= WARN_INTERVAL_MILLIS && lastWarnAt.compareAndSet(last, now))
                LOG.warn("nop.xlang.debug-web.outbound-overflow:session={},capacity={}",
                        session, QUEUE_CAPACITY);
            return false;
        }
        ensureDrain();
        return true;
    }

    /**
     * 关闭通道（onSessionDestroyed 移除时调用）：拒绝新 offer；已在队列的余帧由
     * drain 尽发后退出（B-1 裁定——sessionClosed 等终态帧先入队后 close，必须可达）。
     */
    public void close() {
        if (state.getAndSet(ST_CLOSED) != ST_CLOSED) {
            Thread t = drainThread;
            if (t != null)
                LockSupport.unpark(t);
            ensureDrain(); // 关闭时机在 idle 退出后：补启一次 drain 清空余帧
        }
    }

    private void ensureDrain() {
        if (state.get() != ST_IDLE || !state.compareAndSet(ST_IDLE, ST_RUNNING))
            return;
        Thread t = new Thread(this::drainLoop, "xlang-debug-outbound-" + System.identityHashCode(session));
        t.setDaemon(true);
        drainThread = t;
        t.start();
    }

    private void drainLoop() {
        try {
            List<String> batch = new ArrayList<>(8);
            long idleStart = 0;
            while (true) {
                int st = state.get();
                if (st == ST_IDLE && !state.compareAndSet(ST_IDLE, ST_RUNNING))
                    continue; // CAS 失败 = 并发 close/重启，重读状态
                if (st == ST_DONE)
                    return;
                String first = queue.poll(200, TimeUnit.MILLISECONDS);
                if (first == null) {
                    boolean closed = state.get() == ST_CLOSED;
                    if (queue.isEmpty()) {
                        if (closed) {
                            // 关闭且余帧已尽：终态退出（close 语义 = 尽发余帧）
                            state.set(ST_DONE);
                            return;
                        }
                        if (session.isClosed()) {
                            // 断连且无待发帧：退出（后续 offer 会重启 drain，帧在 send 处被丢弃）
                            if (state.compareAndSet(ST_RUNNING, ST_IDLE))
                                return;
                            continue;
                        }
                        long now = System.currentTimeMillis();
                        if (idleStart == 0)
                            idleStart = now;
                        else if (now - idleStart >= IDLE_TIMEOUT_MILLIS) {
                            // 空闲自退出；再次 offer 时 ensureDrain 重启
                            if (state.compareAndSet(ST_RUNNING, ST_IDLE))
                                return;
                            continue;
                        }
                    }
                    continue;
                }
                idleStart = 0;
                batch.clear();
                batch.add(first);
                queue.drainTo(batch, 64);
                for (String json : batch) {
                    if (session.isClosed())
                        continue; // 断连后帧丢弃（宽限路径事件可达性 = close 前窗口 + open 连接不受影响）
                    try {
                        directSend(json);
                    } catch (Exception e) {
                        // 发送失败：连接已死（关闭码由容器 onClose 处理），余帧照常快速排空
                        LOG.debug("nop.xlang.debug-web.outbound-send-fail:session={}", session, e);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void directSend(String json) {
        synchronized (session) {
            if (session.isClosed())
                return;
            try {
                session.sendMessage(json);
            } catch (Exception e) {
                // 发送失败：连接已死，静默（与既有 bridge 行为一致）
            }
        }
    }
}
