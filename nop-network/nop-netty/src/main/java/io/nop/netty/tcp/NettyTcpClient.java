/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.netty.tcp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.nop.api.core.exceptions.NopConnectException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.netty.NopNettyConstants;
import io.nop.netty.channel.INettyChannelInitializer;
import io.nop.netty.config.NettyTcpClientConfig;
import io.nop.netty.handlers.CloseOnErrorHandler;
import io.nop.netty.handlers.IRpcMessageHandler;
import io.nop.netty.ssl.ISslEngineFactory;
import io.nop.netty.utils.NettyChannelHelper;
import io.nop.netty.utils.NettyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.netty.NopNettyErrors.ERR_TCP_CONNECT_FAIL;

public class NettyTcpClient extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(NettyTcpClient.class);

    private NettyTcpClientConfig config;
    private INettyChannelInitializer channelInitializer;

    private ISslEngineFactory sslEngineFactory;

    private Bootstrap bootstrap;

    private EventLoopGroup workerGroup;

    private volatile ChannelFuture connectFuture;

    private final AtomicInteger connectFailCount = new AtomicInteger();
    private IRetryPolicy<NettyTcpClient> retryPolicy;
    private String remoteHost;
    private int remotePort;

    public IRetryPolicy<NettyTcpClient> getRetryPolicy() {
        return retryPolicy;
    }

    public void setRetryPolicy(IRetryPolicy<NettyTcpClient> retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    public void setWorkerGroup(EventLoopGroup workerGroup) {
        this.workerGroup = workerGroup;
    }

    public ISslEngineFactory getSslEngineFactory() {
        return sslEngineFactory;
    }

    public void setSslEngineFactory(ISslEngineFactory sslEngineFactory) {
        this.sslEngineFactory = sslEngineFactory;
    }

    public NettyTcpClientConfig getConfig() {
        return config;
    }

    public void setConfig(NettyTcpClientConfig config) {
        this.config = config;
    }

    public INettyChannelInitializer getChannelInitializer() {
        return channelInitializer;
    }

    public void setChannelInitializer(INettyChannelInitializer channelInitializer) {
        this.channelInitializer = channelInitializer;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public String getRemoteHost() {
        if (remoteHost != null)
            return remoteHost;
        return config.getRemoteHost();
    }

    public int getRemotePort() {
        if (remotePort > 0)
            return remotePort;
        return config.getRemotePort();
    }

    @Override
    protected void doStart() {
        createBootstrap();
        if (retryPolicy == null) {
            retryPolicy = RetryPolicy.<NettyTcpClient>createRetryPolicy()
                    .withMaxRetryCount(config.getMaxConnectRetryCount())
                    .withMaxRetryDelay(config.getMaxConnectRetryDelay())
                    .withJitterRatio(0.3)
                    .withExponentialDelay(true)
                    .withRetryDelay(config.getConnectRetryDelay());
        }
        doConnect();
    }

    protected void createBootstrap() {
        EventLoopGroup workerGroup = this.createWorkerGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup);

        if (workerGroup instanceof NioEventLoopGroup) {
            bootstrap.channel(NioSocketChannel.class);
        } else {
            bootstrap.channel(EpollSocketChannel.class);
        }

        this.applyOptions(bootstrap);

        bootstrap.handler(new ChannelInitializer<>() {
            @Override
            public void initChannel(Channel ch) {
                doInitChannel(ch);
            }
        });
        this.bootstrap = bootstrap;
    }

    protected void doInitChannel(Channel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addFirst(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                super.channelInactive(ctx);
                scheduleReconnect(ctx.channel().eventLoop(), null);
            }
        });

        NettyChannelHelper.addCommonChannelHandler(ch, config, true, sslEngineFactory);
        this.channelInitializer.initChannel(pipeline);

        if (config.isChannelCloseOnError() && pipeline.get(NopNettyConstants.HANDLER_CLOSE_ON_ERROR) == null) {
            pipeline.addLast(NopNettyConstants.HANDLER_CLOSE_ON_ERROR, CloseOnErrorHandler.INSTANCE);
        }
    }

    protected EventLoopGroup createWorkerGroup() {
        if (workerGroup != null)
            return workerGroup;
        return NettyHelper.newEventGroup(config);
    }

    protected void applyOptions(Bootstrap bootstrap) {
        bootstrap.option(ChannelOption.WRITE_BUFFER_WATER_MARK, config.getWriteBufferWaterMark());

        bootstrap.option(ChannelOption.TCP_NODELAY, config.isTcpNoDelay());

        bootstrap.option(ChannelOption.SO_KEEPALIVE, config.isTcpKeepAlive());
        if (config.getConnectTimeout() > 0)
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout());

        // 在非阻塞的 Socket情况下不建议设置SO_LINGER参数。
        // 如果设置了 SO_LINGER，并且制定了超时时间，这时，我们调用 closesocket方法，方法不能立即完成的话，会抛出WSAEWOULDBLOCK 错误
        // 但是，这个 socket此时还是有效的，可以一段时间之后再次调用 close方法进行关闭尝试。
    }

    private void doConnect() {
        if (isStopping()) {
            return;
        }

        String remoteHost = this.getRemoteHost();
        int remotePort = this.getRemotePort();

        ChannelFuture future = bootstrap.connect(new InetSocketAddress(remoteHost, remotePort));
        this.connectFuture = future;
        future.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture f) {
                if (!f.isSuccess()) {
                    connectFailCount.incrementAndGet();
                    scheduleReconnect(f.channel().eventLoop(), f.cause());
                } else {
                    // 成功连接后重置失败次数为0
                    connectFailCount.set(0);
                    LOG.info("nop.netty.connect-ok:remoteHost={},remotePort={},localAddr={}",
                            getRemoteHost(), getRemotePort(), f.channel().localAddress());
                }
            }
        });
    }

    protected void scheduleReconnect(EventLoopGroup eventLoop, Throwable e) {
        if (isStopping())
            return;

        if (config.isAutoReconnect()) {
            if (connectFailCount.get() % 1000 == 1) {
                LOG.info("nop.netty.client.reconnect:removeHost={},remotePort={},failCount={}", getRemoteHost(), getRemotePort(),
                        connectFailCount.get());
            } else {
                LOG.debug("nop.netty.client.reconnect:removeHost={},remotePort={},failCount={}", getRemoteHost(), getRemotePort(),
                        connectFailCount.get());
            }

            eventLoop.schedule(this::doConnect, getConnectRetryDelay(e), TimeUnit.MILLISECONDS);
        } else if (e != null) {
            LOG.error("nop.netty.client.connect-fail:removeHost={},remotePort={},failCount={}", getRemoteHost(), getRemotePort(),
                    connectFailCount.get());
        }
    }

    private long getConnectRetryDelay(Throwable e) {
        return retryPolicy.getRetryDelay(e, connectFailCount.get(), this);
    }

    @Override
    protected void doStop() {
        ChannelFuture f = this.connectFuture;
        if (f != null) {
            NettyHelper.safeClose(f.channel());
        }

        Bootstrap bootstrap = this.bootstrap;
        if (bootstrap != null) {
            // 如果不是外部创建的workerGroup，那么需要主动关闭
            if (this.workerGroup == null) {
                bootstrap.config().group().shutdownGracefully();
            }
        }
    }

    public ChannelFuture getConnectFuture() {
        Guard.notNull(connectFuture, "nop.netty.client-not-started");
        return connectFuture;
    }

    public boolean awaitConnected(long timeout) {
        try {
            return getConnectFuture().await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw NopConnectException.adapt(e);
        }
    }

    public Object send(Object msg, int timeout) {
        return FutureHelper.syncGet(sendAsync(msg, timeout));
    }

    public void sendOneway(Object msg) {
        ChannelFuture channelFuture = getConnectFuture();

        if (channelFuture.isDone()) {
            channelFuture.channel().write(msg);
        } else {
            channelFuture.addListener(new GenericFutureListener<>() {
                @Override
                public void operationComplete(Future<? super Void> future) {
                    channelFuture.channel().write(msg);
                    channelFuture.removeListener(this);
                }
            });
        }
    }

    public CompletableFuture<Object> sendAsync(Object msg, int timeout) {
        Guard.checkArgument(timeout > 0);
        CompletableFuture<Object> ret = new CompletableFuture<>();
        ChannelFuture channelFuture = getConnectFuture();

        if (channelFuture.isDone()) {
            doSend(channelFuture, msg, timeout, ret);
        } else {
            channelFuture.addListener(new GenericFutureListener<>() {
                @Override
                public void operationComplete(Future<? super Void> future) {
                    doSend(channelFuture, msg, timeout, ret);
                    channelFuture.removeListener(this);
                }
            });
        }
        return ret;
    }

    private void doSend(ChannelFuture channelFuture, Object msg, int timeout, CompletableFuture<Object> ret) {
        if (channelFuture.isSuccess()) {
            IRpcMessageHandler handler = channelFuture.channel().pipeline().get(IRpcMessageHandler.class);
            handler.send(msg, timeout, ret);
        } else {
            LOG.info("nop.netty.client.send.not-connected:remoteHost={},remotePort={}",
                    getRemoteHost(), getRemotePort());
            Throwable error = channelFuture.cause();
            ret.completeExceptionally(new NopConnectException(ERR_TCP_CONNECT_FAIL).cause(error));
        }
    }

}