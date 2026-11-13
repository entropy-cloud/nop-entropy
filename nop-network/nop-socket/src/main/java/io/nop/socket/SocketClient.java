/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.socket;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.retry.RetryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import static io.nop.socket.SocketErrors.ARG_HOST;
import static io.nop.socket.SocketErrors.ARG_PORT;
import static io.nop.socket.SocketErrors.ERR_SOCKET_CONCURRENT_READ;
import static io.nop.socket.SocketErrors.ERR_SOCKET_CONNECT_FAIL;
import static io.nop.socket.SocketErrors.ERR_SOCKET_NOT_CONNECTED;
import static io.nop.socket.SocketErrors.ERR_SOCKET_READ_FAIL;
import static io.nop.socket.SocketErrors.ERR_SOCKET_READ_TIMEOUT;
import static io.nop.socket.SocketErrors.ERR_SOCKET_WRITE_FAIL;

/**
 * Socket 命令客户端。
 *
 * <h3>单读线程契约（4.3-C1-016，plan 2561 裁定 B）</h3>
 *
 * {@code headerBuf} 与底层 {@link InputStream} 为实例共享且非线程安全。
 * {@link #call(BinaryCommand)}（调用线程）与 {@link #recv(Executor, Consumer)}
 * 启动的 recv-loop（executor 线程）是同一连接上的两个读入口，通过
 * {@code readerActive}（AtomicBoolean）互斥：任一时刻最多允许一个读线程在同一
 * 连接上读帧，call 与 recv-loop 并发（或同一入口自身重入）将以
 * {@link SocketErrors#ERR_SOCKET_CONCURRENT_READ} 拒绝。禁止绕开本守卫直接
 * 共享同一 InputStream 交错读帧。
 */
public class SocketClient implements ICommandClient {
    static final Logger LOG = LoggerFactory.getLogger(SocketClient.class);

    private Socket socket;
    private InputStream is;
    private OutputStream os;
    private ByteBuffer headerBuf = ByteBuffer.allocate(8);
    private volatile boolean closed;

    // 4.3-C1-016（plan 2561 裁定 B）：call 与 recv-loop 双入口互斥守卫（共享 headerBuf/is 单读线程契约）
    private final AtomicBoolean readerActive = new AtomicBoolean(false);

    private LongAdder sendCount = new LongAdder();
    private LongAdder recvCount = new LongAdder();
    private LongAdder sendHeartbeatCount = new LongAdder();
    private LongAdder recvHeartbeatCount = new LongAdder();

    private volatile long lastWriteTime = CoreMetrics.currentTimeMillis();
    private IScheduledExecutor timer;
    private Future<?> heartbeatFuture;

    private ClientConfig config = new ClientConfig();

    private AtomicInteger connectFailCount = new AtomicInteger();

    // 4.3-C1-005（plan 2560 裁定 K）：volatile——晚设置回调对连接事件线程可见
    private volatile Runnable onChannelOpen;
    private volatile Runnable onChannelClose;

    public void setTimer(IScheduledExecutor timer) {
        this.timer = timer;
    }

    public void setOnChannelOpen(Runnable onOpen) {
        this.onChannelOpen = onOpen;
    }

    public void setOnChannelClose(Runnable onClose) {
        this.onChannelClose = onClose;
    }

    @Override
    public Runnable getOnChannelOpen() {
        return onChannelOpen;
    }

    @Override
    public Runnable getOnChannelClose() {
        return onChannelClose;
    }

    public IScheduledExecutor getTimer() {
        return timer;
    }

    public ClientConfig getClientConfig() {
        return config;
    }

    public long getSendCmdCount() {
        return sendCount.longValue();
    }

    public long getRecvCmdCount() {
        return recvCount.longValue();
    }

    public long getSendHeartbeatCount() {
        return sendHeartbeatCount.longValue();
    }

    public long getRecvHeartbeatCount() {
        return recvHeartbeatCount.longValue();
    }

    public int getConnectFailCount() {
        return connectFailCount.get();
    }

    // 测试缝（4.3-C1-016 / 4.3-C3-026）：观察读守卫与 closed 终态
    boolean isReaderActive() {
        return readerActive.get();
    }

    boolean isClosed() {
        return closed;
    }

    public void setClientConfig(ClientConfig config) {
        this.config = config;
    }

    public synchronized void connect() {
        Guard.checkState(socket == null);
        Socket socket = new Socket();
        try {
            socket.setKeepAlive(true);
            socket.connect(new InetSocketAddress(config.getHost(), config.getPort()), config.getConnectTimeout());
            socket.setSoTimeout(config.getReadTimeout());

            is = socket.getInputStream();
            os = socket.getOutputStream();
            this.socket = socket;
        } catch (IOException e) {
            // 连接失败必须清理半开 socket，否则既泄漏 fd 又导致无法重试 connect
            IoHelper.safeCloseObject(socket);
            throw new NopException(ERR_SOCKET_CONNECT_FAIL, e).param(ARG_HOST, config.getHost()).param(ARG_PORT,
                    config.getPort());
        }

        try {
            if (onChannelOpen != null)
                onChannelOpen.run();

            if (config.getHeartbeatInterval() > 1) {
                if (timer != null) {
                    heartbeatFuture = timer.scheduleWithFixedDelay(this::heartbeat, config.getHeartbeatInterval(),
                            config.getHeartbeatInterval() / 2, TimeUnit.MILLISECONDS);
                }
            }
        } catch (RuntimeException e) {
            // 4.3-C6-027（plan 2561 裁定 N）：回调段/心跳段异常必须回收连接并复位状态，
            // 否则 Guard.checkState(socket==null) 锁死重试入口
            IoHelper.safeCloseObject(socket);
            cleanup();
            throw e;
        }
    }

    public synchronized void reconnect() {
        cleanup();
        connect();
    }

    private void heartbeat() {
        if (closed)
            return;
        if (CoreMetrics.currentTimeMillis() - lastWriteTime > config.getHeartbeatInterval()) {
            try {
                send(newHeartbeat(), true);
            } catch (Exception e) {
                LOG.trace("nop.socket.send-heart-fail", e);
            }
        }
    }

    private BinaryCommand newHeartbeat() {
        return BinaryCommand.newEmptyCommand(config.getMasks(), (short) 0x0);
    }

    public void ping() {
        send(newHeartbeat(), true);
    }

    public BinaryCommand recv() {
        try {
            do {
                InputStream is = getInputStream();
                if (is == null)
                    return null;

                BinaryCommand cmd = BinaryCommand.readPacketFromStream(is, config.getMasks(), config.getMinDataLen(),
                        config.getMaxDataLen(), headerBuf);
                if (cmd != null) {
                    if (cmd.getCmd() == 0) {
                        recvHeartbeatCount.increment();
                    } else {
                        recvCount.increment();
                        return cmd;
                    }
                } else {
                    return null;
                }
            } while (true);
        } catch (SocketTimeoutException e) {
            throw new NopException(ERR_SOCKET_READ_TIMEOUT, e).param(ARG_HOST, config.getHost()).param(ARG_PORT,
                    config.getPort());
        } catch (Exception e) {
            throw new NopException(ERR_SOCKET_READ_FAIL, e).param(ARG_HOST, config.getHost()).param(ARG_PORT,
                    config.getPort());
        }
    }

    synchronized InputStream getInputStream() {
        return is;
    }

    public void recv(Executor executor, Consumer<BinaryCommand> handler) {
        executor.execute(() -> {
            // 4.3-C1-016（plan 2561 裁定 B）：recv-loop 入口同样受 readerActive 守卫约束
            if (!readerActive.compareAndSet(false, true)) {
                LOG.error("nop.socket.concurrent-recv-loop-rejected");
                return;
            }
            try {
                doRecv(handler);
            } catch (Exception e) {
                LOG.error("nop.socket.recv-fail", e);
            } finally {
                readerActive.set(false);
            }

            if (!closed) {
                if (config.isAutoReconnect() && config.getReconnectPolicy() != null) {
                    RetryHelper.retryExecute(() -> {
                        if (closed)
                            return null;

                        // reconnect在executor而不是timers上执行，避免阻塞timer
                        return FutureHelper.runOnExecutor(executor, () -> {
                            reconnect();
                            return null;
                        });
                    }, config.getReconnectPolicy(), timer, null).thenRun(() -> {
                        recv(executor, handler);
                    }).exceptionally(e -> {
                        // 4.3-C3-026（plan 2561 裁定 H）：重连链终态处理器——重试彻底失败必须收口
                        LOG.error("nop.socket.reconnect-fail-terminal", e);
                        closed = true;
                        cleanup();
                        return null;
                    });
                }
            }
        });
    }

    private void doRecv(Consumer<BinaryCommand> handler) {
        while (!closed) {
            // IO 异常路径：recv() 内部抛出 typed NopException，向上传播以触发重连
            BinaryCommand cmd = recv();
            if (cmd == null) {
                break;
            }
            try {
                handler.accept(cmd);
            } catch (Exception e) {
                // 4.3-C3-026（plan 2561 裁定 H）：业务回调异常与 IO 异常分流——回调失败不终止 recv 循环
                LOG.error("nop.socket.recv-handler-fail", e);
            }
        }
        LOG.info("socket.recv-exit");
    }

    public BinaryCommand call(BinaryCommand request) {
        // 4.3-C1-016（plan 2561 裁定 B）：与 recv-loop 双入口互斥，共享 headerBuf/is 禁止并发读
        if (!readerActive.compareAndSet(false, true))
            throw new NopException(ERR_SOCKET_CONCURRENT_READ).param(ARG_HOST, config.getHost()).param(ARG_PORT,
                    config.getPort());
        try {
            send(request, true);
            return recv();
        } finally {
            readerActive.set(false);
        }
    }

    public void send(BinaryCommand request, boolean flush) {
        if (closed) {
            LOG.warn("nop.warn.socket.send-after-closed");
            return;
        }

        try {
            OutputStream os = getOutputStream();
            if (os == null) {
                // 4.3-C3-026（plan 2561 裁定 H）：未连接时静默丢弃改为 typed 异常
                throw new NopException(ERR_SOCKET_NOT_CONNECTED).param(ARG_HOST, config.getHost()).param(ARG_PORT,
                        config.getPort());
            }

            synchronized (os) {
                BinaryCommand.writePacketToStream(request, os);
                if (request.getCmd() == 0) {
                    sendHeartbeatCount.increment();
                } else {
                    sendCount.increment();
                }
                lastWriteTime = CoreMetrics.currentTimeMillis();
                if (flush)
                    os.flush();
            }
        } catch (IOException e) {
            throw new NopException(ERR_SOCKET_WRITE_FAIL, e).param(ARG_HOST, config.getHost()).param(ARG_PORT,
                    config.getPort());
        }
    }

    synchronized OutputStream getOutputStream() {
        return os;
    }

    public synchronized void close() {
        closed = true;
        cleanup();
    }

    synchronized void cleanup() {
        if (socket != null) {
            IoHelper.safeCloseObject(socket);
            socket = null;
        }

        is = null;
        os = null;

        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
        }

        if (onChannelClose != null)
            onChannelClose.run();
    }
}