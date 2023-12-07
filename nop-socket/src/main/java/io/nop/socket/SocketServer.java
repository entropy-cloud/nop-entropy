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
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.thread.ThreadHelper;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.commons.util.IoHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import static io.nop.socket.SocketErrors.ERR_SOCKET_ACCEPT_FAIL;
import static io.nop.socket.SocketErrors.ERR_SOCKET_WRITE_FAIL;

public class SocketServer extends LifeCycleSupport implements ICommandServer {
    static final Logger LOG = LoggerFactory.getLogger(SocketServer.class);

    private ICommandHandler handler;
    private Executor executor;
    private volatile boolean stopped;
    private ServerSocket socket;
    private final AtomicInteger connectionCount = new AtomicInteger();
    private final LongAdder recvCmdCount = new LongAdder();
    private final LongAdder recvHeartbeatCount = new LongAdder();

    private final ConcurrentMap<String, Socket> connections = new ConcurrentHashMap<>();
    private Consumer<String> onChannelOpen;
    private Consumer<String> onChannelClose;

    private ServerConfig config = new ServerConfig();

    public ServerConfig getServerConfig() {
        return config;
    }

    public void setServerConfig(ServerConfig config) {
        this.config = config;
    }

    public String getHost() {
        return config.getHost();
    }

    public int getPort() {
        return config.getPort();
    }

    public long getRecvCmdCount() {
        return recvCmdCount.longValue();
    }

    public long getRevHeartbeatCount() {
        return recvHeartbeatCount.longValue();
    }

    public ICommandHandler getHandler() {
        return handler;
    }

    public void setHandler(ICommandHandler handler) {
        this.handler = handler;
    }

    public ServerSocket getSocket() {
        return socket;
    }

    public void setSocket(ServerSocket socket) {
        this.socket = socket;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public void setCommandHandler(ICommandHandler handler) {
        this.handler = handler;
    }

    public Consumer<String> getOnChannelOpen() {
        return onChannelOpen;
    }

    public void setOnChannelOpen(Consumer<String> onChannelOpen) {
        this.onChannelOpen = onChannelOpen;
    }

    public Consumer<String> getOnChannelClose() {
        return onChannelClose;
    }

    public void setOnChannelClose(Consumer<String> onChannelClose) {
        this.onChannelClose = onChannelClose;
    }

    protected synchronized void doStart() {
        if (socket != null)
            return;

        try {
            socket = new ServerSocket();
            socket.bind(new InetSocketAddress(config.getHost(), config.getPort()));
            socket.setReuseAddress(true);
        } catch (IOException e) {
            LOG.info("nop.socket.start-server-fail:host={},port={}", config.getHost(), config.getPort());
            throw new RuntimeException("nop.socket.start-server-fail:" + config.getPort(), e);
        }
        LOG.info("nop.socket.start-server:serverName={},host={},port={}", config.getServerName(), config.getHost(),
                config.getPort());

        if (executor == null) {
            executor = GlobalExecutors.cachedThreadPool();
        }
        executor.execute(this::run);
    }

    @Override
    protected void doStop() {
        LOG.info("nop.socket.stop-server:host={},port={}", config.getHost(), config.getPort());

        stopped = true;
        closeAllConnections();
        IoHelper.safeCloseObject(socket);
    }

    public boolean waitConnected(long timeout) {
        long endTime = CoreMetrics.currentTimeMillis() + timeout;
        while (connections.isEmpty()) {
            ThreadHelper.sleep(100);
            if (CoreMetrics.currentTimeMillis() >= endTime)
                return false;
        }
        return true;
    }

    public void broadcast(BinaryCommand command) {
        for (Socket socket : connections.values()) {
            try {
                OutputStream os = socket.getOutputStream();
                BinaryCommand.writePacketToStream(command, os);
                os.flush();
            } catch (Exception e) {
                LOG.info("nop.socket.write-fail", e);
            }
        }
    }

    public void sendTo(String addr, BinaryCommand command) {
        Socket socket = getConnection(addr);
        if (socket == null) {
            LOG.warn("nop.socket.send-to-invalid-addr:addr={}", addr);
            return;
        }

        try {
            OutputStream os = socket.getOutputStream();
            BinaryCommand.writePacketToStream(command, os);
            os.flush();
        } catch (Exception e) {
            throw new NopException(ERR_SOCKET_WRITE_FAIL, e);
        }
    }

    private void closeAllConnections() {
        Iterator<Socket> it = connections.values().iterator();
        while (it.hasNext()) {
            IoHelper.safeCloseObject(it.next());
            it.remove();
        }
    }

    void run() {
        do {
            try {
                Socket client = socket.accept();
                if (connectionCount.get() + 1 > config.getMaxConnections()) {
                    LOG.info("nop.socket.connection-count-exceed-limit:maxConn={}", config.getMaxConnections());
                    client.close();
                    continue;
                }

                connectionCount.incrementAndGet();

                String ip = client.getInetAddress().getHostAddress();
                int port = client.getPort();
                String addr = getConnectionKey(ip, port);
                connections.put(addr, client);

                LOG.info("nop.socket.accept-client:ip={},port={}", ip, port);

                try {
                    executor.execute(() -> processCommand(addr, client));
                } catch (Exception e) {
                    // 如果队列已满
                    removeSocket(client);
                    throw e;
                }
            } catch (Exception e) {
                if (stopped)
                    break;
                throw new NopException(ERR_SOCKET_ACCEPT_FAIL, e);
            }
        } while (!stopped);
        LOG.info("nop.socket.server-exit:host={},port={}", config.getHost(), config.getPort());
    }

    void removeSocket(Socket client) {
        String ip = client.getInetAddress().toString();
        int port = client.getPort();
        String addr = ip + ':' + port;

        LOG.info("nop.socket.destroy-client:ip={},port={}", ip, port);

        connectionCount.decrementAndGet();
        connections.remove(addr, client);

        IoHelper.safeCloseObject(client);
    }

    public String getConnectionKey(Socket client) {
        String ip = client.getInetAddress().toString();
        int port = client.getPort();
        return getConnectionKey(ip, port);
    }

    public String getConnectionKey(String ip, int port) {
        return ip + ':' + port;
    }

    public Socket getConnection(String addr) {
        return connections.get(addr);
    }

    void processCommand(String addr, Socket socket) {
        ByteBuffer headerBuf = ByteBuffer.allocate(8);
        try {
            if (config.getIdleTimeout() > 0) {
                socket.setSoTimeout(config.getIdleTimeout());
            }

            InputStream is = socket.getInputStream();
            OutputStream os = socket.getOutputStream();

            if (onChannelOpen != null)
                onChannelOpen.accept(addr);

            while (!stopped) {
                BinaryCommand request = BinaryCommand.readPacketFromStream(is, config.getMasks(),
                        config.getMinDataLen(), config.getMaxDataLen(), headerBuf);
                if (request == null)
                    break;
                if (request.getCmd() == 0) {
                    // 接收到心跳消息，直接忽略处理
                    recvHeartbeatCount.increment();
                    continue;
                } else {
                    recvCmdCount.increment();
                }
                BinaryCommand response = handler.onCommand(addr, request);
                if (response != null) {
                    BinaryCommand.writePacketToStream(response, os);
                    os.flush();
                }
            }
        } catch (Exception e) {
            if (!stopped) {
                LOG.error("nop.socket.process-cmd-fail", e);
            }
        } finally {
            if (onChannelClose != null) {
                try {
                    onChannelClose.accept(addr);
                } catch (Exception e) {
                    LOG.error("nop.socket.on-channel-close-fail", e);
                }
            }
            removeSocket(socket);
        }
    }
}