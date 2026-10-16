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

/**
 * 内存 WS 会话桩：线程安全捕获出站帧与关闭码（引擎回调线程/容器线程/定时器三写者）。
 */
public class MockWsSession implements IWebSocketSession {
    private final List<String> sent = new CopyOnWriteArrayList<>();
    private volatile boolean closed;
    private volatile short closeCode;
    private volatile String closeReason;

    @Override
    public void sendMessage(String message) {
        if (closed)
            throw new IllegalStateException("session closed");
        sent.add(message);
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
