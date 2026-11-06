/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.graphql.core.ws.IWebSocketSession;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * 内存 WS 会话桩：线程安全捕获出站帧与关闭码（引擎回调线程/容器线程/定时器三写者）。
 *
 * <p>plan 2479 停滞客户端测试扩桩：{@link #setBlocked(CountDownLatch)} 使
 * sendMessage 可编程阻塞（latch 可中断——tearDown shutdownNow 能解除，不泄漏线程）；
 * {@link #sentCount()} 供阻塞观测。
 */
public class MockWsSession implements IWebSocketSession {
    private final List<String> sent = new CopyOnWriteArrayList<>();
    private volatile boolean closed;
    private volatile short closeCode;
    private volatile String closeReason;
    private volatile CountDownLatch blockLatch;

    @Override
    public void sendMessage(String message) {
        CountDownLatch latch = blockLatch;
        if (latch != null) {
            try {
                // 上限只作挂死保护、不承载语义：用例在 finally 中放行停滞桩；无界 latch 会令
                // drain 线程长期持有 session 监视器、在全量套件下跨测试楔死（plan 2479 执行勘误）
                latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (closed)
            throw new IllegalStateException("session closed");
        sent.add(message);
    }

    /** 可编程停滞：非 null 时 sendMessage 阻塞直至 countDown（可中断）。 */
    public void setBlocked(CountDownLatch latch) {
        this.blockLatch = latch;
    }

    public int sentCount() {
        return sent.size();
    }

    @Override
    public void close(short statusCode, String reason) {
        if (closed)
            return;
        this.closeCode = statusCode;
        this.closeReason = reason;
        this.closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    public List<String> getSent() {
        return sent;
    }

    /** 快照（断言用，避免遍历中并发追加）。 */
    public List<String> snapshot() {
        return new ArrayList<>(sent);
    }

    public short getCloseCode() {
        return closeCode;
    }

    public String getCloseReason() {
        return closeReason;
    }
}
