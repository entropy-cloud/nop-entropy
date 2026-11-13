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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import static io.nop.socket.SocketErrors.ERR_SOCKET_ACCEPT_FAIL;
import static io.nop.socket.SocketErrors.ARG_HOST;
import static io.nop.socket.SocketErrors.ARG_PORT;
import static io.nop.socket.SocketErrors.ERR_SOCKET_START_SERVER_FAIL;
import static io.nop.socket.SocketErrors.ERR_SOCKET_WRITE_FAIL;

public class SocketServer extends LifeCycleSupport implements ICommandServer {
    static final Logger LOG = LoggerFactory.getLogger(SocketServer.class);

    private ICommandHandler handler;
    private Executor executor;
    private volatile boolean stopped;
    // 4.3-C1-019（plan 2561 裁定 D）：per-start accept 世代——run 循环退出判定不再复用共享单比特 stopped，
    // restart 后旧 accept 循环凭世代失配退出，不误杀/抢占新循环
    private final AtomicLong acceptGeneration = new AtomicLong();
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
            // 必须在 bind 之前设置 SO_REUSEADDR 才会生效
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(config.getHost(), config.getPort()));
        } catch (IOException e) {
            IoHelper.safeCloseObject(socket);
            socket = null;
            LOG.info("nop.socket.start-server-fail:host={},port={}", config.getHost(), config.getPort());
            throw new NopException(ERR_SOCKET_START_SERVER_FAIL, e).param(ARG_HOST, config.getHost()).param(ARG_PORT, config.getPort());
        }
        LOG.info("nop.socket.start-server:serverName={},host={},port={}", config.getServerName(), config.getHost(),
                config.getPort());

        if (executor == null) {
            executor = GlobalExecutors.cachedThreadPool();
        }
        // 支持重启：清停止标志后重新进入 accept 循环
        stopped = false;
        // 4.3-C1-019（plan 2561 裁定 D）：本次 start 分配新的 accept 世代，run 循环退出判定按世代进行
        long generation = acceptGeneration.incrementAndGet();
        executor.execute(() -> run(generation));
    }

    @Override
    protected void doStop() {
        LOG.info("nop.socket.stop-server:host={},port={}", config.getHost(), config.getPort());

        stopped = true;
        // 4.3-C1-019（plan 2561 裁定 D）：stop 即作废当前 accept 世代，旧 run 循环立刻失配退出
        acceptGeneration.incrementAndGet();
        closeAllConnections();
        IoHelper.safeCloseObject(socket);
        socket = null;
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
                // 与处理线程的响应写入并发时必须同步，避免帧交错损坏流
                synchronized (os) {
                    BinaryCommand.writePacketToStream(command, os);
                    os.flush();
                }
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
            synchronized (os) {
                BinaryCommand.writePacketToStream(command, os);
                os.flush();
            }
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
        run(acceptGeneration.get());
    }

    void run(long generation) {
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
                    // 如果队列已满，丢弃该连接但 accept 循环必须存活
                    LOG.error("nop.socket.dispatch-connection-fail:addr={}", addr, e);
                    removeSocket(client);
                }
            } catch (Exception e) {
                // 4.3-C1-019（plan 2561 裁定 D）：世代失配（restart 后旧循环）同样立即退出
                if (stopped || acceptGeneration.get() != generation)
                    break;
                // 瞬时 accept 异常（如 fd 耗尽）不应终止整个 accept 循环
                LOG.error("nop.socket.accept-fail", e);
                ThreadHelper.sleep(1000);
            }
        } while (!stopped && acceptGeneration.get() == generation);
        LOG.info("nop.socket.server-exit:host={},port={}", config.getHost(), config.getPort());
    }

    void removeSocket(Socket client) {
        String ip = client.getInetAddress().getHostAddress();
        int port = client.getPort();
        String addr = getConnectionKey(ip, port);

        LOG.info("nop.socket.destroy-client:ip={},port={}", ip, port);

        connectionCount.decrementAndGet();
        connections.remove(addr, client);

        IoHelper.safeCloseObject(client);
    }

    public String getConnectionKey(Socket client) {
        String ip = client.getInetAddress().getHostAddress();
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
                    synchronized (os) {
                        BinaryCommand.writePacketToStream(response, os);
                        os.flush();
                    }
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