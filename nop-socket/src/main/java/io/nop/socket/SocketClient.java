/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import static io.nop.socket.SocketErrors.ARG_HOST;
import static io.nop.socket.SocketErrors.ARG_PORT;
import static io.nop.socket.SocketErrors.ERR_SOCKET_CONNECT_FAIL;
import static io.nop.socket.SocketErrors.ERR_SOCKET_READ_FAIL;
import static io.nop.socket.SocketErrors.ERR_SOCKET_READ_TIMEOUT;
import static io.nop.socket.SocketErrors.ERR_SOCKET_WRITE_FAIL;

public class SocketClient implements ICommandClient {
    static final Logger LOG = LoggerFactory.getLogger(SocketClient.class);

    private Socket socket;
    private InputStream is;
    private OutputStream os;
    private ByteBuffer headerBuf = ByteBuffer.allocate(8);
    private volatile boolean closed;

    private LongAdder sendCount = new LongAdder();
    private LongAdder recvCount = new LongAdder();
    private LongAdder sendHeartbeatCount = new LongAdder();
    private LongAdder recvHeartbeatCount = new LongAdder();

    private volatile long lastWriteTime = System.currentTimeMillis();
    private IScheduledExecutor timer;
    private Future<?> heartbeatFuture;

    private ClientConfig config = new ClientConfig();

    private AtomicInteger connectFailCount = new AtomicInteger();

    private Runnable onChannelOpen;
    private Runnable onChannelClose;

    public void setTimer(IScheduledExecutor timer) {
        this.timer = timer;
    }

    public void setOnChannelOpen(Runnable onOpen) {
        this.onChannelOpen = onOpen;
    }

    public void setOnChannelClose(Runnable onClose) {
        this.onChannelClose = onClose;
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

    public void setClientConfig(ClientConfig config) {
        this.config = config;
    }

    public synchronized void connect() {
        Guard.checkState(socket == null);
        try {
            socket = new Socket();
            socket.setKeepAlive(true);
            socket.connect(new InetSocketAddress(config.getHost(), config.getPort()), config.getConnectTimeout());
            socket.setSoTimeout(config.getReadTimeout());

            is = socket.getInputStream();
            os = socket.getOutputStream();
        } catch (IOException e) {
            throw new NopException(ERR_SOCKET_CONNECT_FAIL, e).param(ARG_HOST, config.getHost()).param(ARG_PORT,
                    config.getPort());
        }

        if (onChannelOpen != null)
            onChannelOpen.run();

        if (config.getHeartbeatInterval() > 1) {
            if (timer != null) {
                heartbeatFuture = timer.scheduleWithFixedDelay(this::heartbeat, config.getHeartbeatInterval(),
                        config.getHeartbeatInterval() / 2, TimeUnit.MILLISECONDS);
            }
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
            try {
                doRecv(handler);
            } catch (Exception e) {
                LOG.error("nop.socket.recv-fail", e);
            }

            if (!closed) {
                if (config.isAutoReconnect() && config.getReconnectPolicy() != null) {
                    RetryHelper.retryExecute(config.getReconnectPolicy(), timer, () -> {
                        if (closed)
                            return null;

                        // reconnect在executor而不是timers上执行，避免阻塞timer
                        return FutureHelper.runOnExecutor(executor, () -> {
                            reconnect();
                            return null;
                        });
                    }, null).thenRun(() -> {
                        recv(executor, handler);
                    });
                }
            }
        });
    }

    private void doRecv(Consumer<BinaryCommand> handler) {
        while (!closed) {
            BinaryCommand cmd = recv();
            if (cmd == null) {
                break;
            }
            handler.accept(cmd);
        }
        LOG.info("socket.recv-exit");
    }

    public BinaryCommand call(BinaryCommand request) {
        send(request, true);
        return recv();
    }

    public void send(BinaryCommand request, boolean flush) {
        if (closed) {
            LOG.warn("nop.warn.socket.send-after-closed");
            return;
        }

        try {
            OutputStream os = getOutputStream();
            if (os == null) {
                LOG.warn("nop.warn.socket.ignore-send-since-output-stream-is-null");
                return;
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