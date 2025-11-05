/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.netty.tcp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalChannelTrafficShapingHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.NetHelper;
import io.nop.netty.NopNettyConstants;
import io.nop.netty.channel.INettyChannelInitializer;
import io.nop.netty.config.NettyTcpServerConfig;
import io.nop.netty.config.TrafficShapingConfig;
import io.nop.netty.handlers.CloseOnErrorHandler;
import io.nop.netty.handlers.IRpcMessageHandler;
import io.nop.netty.handlers.NettyChannelGroupHandler;
import io.nop.netty.ssl.ISslEngineFactory;
import io.nop.netty.utils.NettyChannelHelper;
import io.nop.netty.utils.NettyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.nop.netty.NopNettyErrors.ERR_NETTY_NO_AVAILABLE_CHANNEL;

public class NettyTcpServer extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(NettyTcpServer.class);

    private NettyTcpServerConfig config;
    private ServerBootstrap bootstrap;
    private EventLoopGroup workerGroup;

    private ChannelGroup channelGroup;

    private INettyChannelInitializer channelInitializer;

    private ISslEngineFactory sslEngineFactory;

    private String host;
    private int port;

    public NettyTcpServerConfig getConfig() {
        return config;
    }

    public void setConfig(NettyTcpServerConfig config) {
        this.config = config;
    }

    public void setChannelInitializer(INettyChannelInitializer channelInitializer) {
        this.channelInitializer = channelInitializer;
    }

    public ISslEngineFactory getSslEngineFactory() {
        return sslEngineFactory;
    }

    public void setSslEngineFactory(ISslEngineFactory sslEngineFactory) {
        this.sslEngineFactory = sslEngineFactory;
    }

    public void setWorkerGroup(EventLoopGroup eventLoop) {
        this.workerGroup = eventLoop;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        if (host != null)
            return host;
        return config.getHost();
    }

    public int getPort() {
        if (port > 0)
            return port;

        return config.getPort();
    }

    public String toString() {
        return "NettyTcpServer[host=" + getHost() + ",port=" + getPort() + "]";
    }


    @Override
    protected void doStart() {
        // 如果没有明确指定port, 则随机生成一个
        int port = getPort();
        if (port <= 0) {
            port = NetHelper.findAvailableTcpPort();
        }

        String host = getHost();
        if (host == null) {
            host = "localhost";
        }

        bootstrap = new ServerBootstrap();
        this.applyOptions(bootstrap);

        EventLoopGroup bossGroup = NettyHelper.newEventGroup(config.getBossGroupSize(), config.isUseEpoll(),
                config.getIoRatio());
        EventLoopGroup workerGroup = this.workerGroup;
        if (workerGroup == null)
            workerGroup = NettyHelper.newEventGroup(config.getWorkerGroupSize(),
                    config.isUseEpoll(),
                    config.getIoRatio());

        bootstrap.group(bossGroup, workerGroup);

        if (workerGroup instanceof NioEventLoopGroup) {
            bootstrap.channel(NioServerSocketChannel.class);
        } else {
            bootstrap.channel(EpollServerSocketChannel.class);
        }

        if (config.isUseChannelGroup()) {
            channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        }

        bootstrap.childHandler(new ChannelInitializer<>() {
            @Override
            public void initChannel(Channel ch) {
                doInitChannel(ch);
            }
        });

        final InetSocketAddress sockAddr = new InetSocketAddress(host, port);

        ChannelFuture future;
        try {
            future = bootstrap.bind(sockAddr).addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    if (future.isSuccess()) {
                        LOG.info("nop.netty.server.start-ok:ip={},port={}", sockAddr.getAddress(), sockAddr.getPort());
                    } else {
                        LOG.info("nop.netty.server.start-failed:ip={},port={}", sockAddr.getAddress(), sockAddr.getPort());
                    }
                }
            }).sync();
        } catch (Exception e) {
            LOG.error("nop.netty.server.bind-port-fail", e);
            throw NopException.adapt(e);
        }

        if (!future.isSuccess()) {
            Throwable e = future.cause();
            LOG.error("nop.netty.server.bind-port-fail", e);
            throw NopException.adapt(e);
        }
    }

    private EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    protected void doInitChannel(Channel ch) {

        ChannelPipeline pipeline = ch.pipeline();

        if (config.getGlobalTrafficShapingConfig() != null) {
            TrafficShapingConfig shapingConfig = config.getGlobalTrafficShapingConfig();
            if (!shapingConfig.isNoLimit()) {
                pipeline.addLast(NopNettyConstants.HANDLER_GLOBAL_TRAFFIC_SHAPING,
                        new GlobalChannelTrafficShapingHandler(getWorkerGroup(),
                                shapingConfig.getWriteLimit(),
                                shapingConfig.getReadLimit(), 0, 0,
                                shapingConfig.getCheckInterval()));
            }
        }

        if (config.getChannelTrafficShapingConfig() != null) {
            TrafficShapingConfig shapingConfig = config.getChannelTrafficShapingConfig();
            if (!shapingConfig.isNoLimit()) {
                pipeline.addLast(NopNettyConstants.HANDLER_CHANNEL_TRAFFIC_SHAPING,
                        new ChannelTrafficShapingHandler(shapingConfig.getWriteLimit(),
                                shapingConfig.getReadLimit(), shapingConfig.getCheckInterval()));
            }
        }

        if (config.isUseChannelGroup()) {
            pipeline.addLast(NopNettyConstants.HANDLER_CHANNEL_GROUP, new NettyChannelGroupHandler(channelGroup));
        }

        NettyChannelHelper.addCommonChannelHandler(ch, config, false, sslEngineFactory);

        channelInitializer.initChannel(ch.pipeline());

        if (config.isChannelCloseOnError() && pipeline.get(NopNettyConstants.HANDLER_CLOSE_ON_ERROR) == null) {
            pipeline.addLast(NopNettyConstants.HANDLER_CLOSE_ON_ERROR, CloseOnErrorHandler.INSTANCE);
        }
    }

    protected void applyOptions(ServerBootstrap bootstrap) {
        bootstrap.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, config.getWriteBufferWaterMark());

        bootstrap.childOption(ChannelOption.TCP_NODELAY, config.isTcpNoDelay());

        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, config.isTcpKeepAlive());

        bootstrap.option(ChannelOption.SO_REUSEADDR, config.isTcpReuseAddress());
        if (config.getTcpAcceptBacklog() > 0)
            bootstrap.option(ChannelOption.SO_BACKLOG, config.getTcpAcceptBacklog());
    }

    public CompletableFuture<Object> sendToChannel(String channelId, Object msg, int timeout) {
        Channel channel = getChannelById(channelId);
        if (channel == null) {
            throw new NopException(ERR_NETTY_NO_AVAILABLE_CHANNEL);
        }

        return sendToChannel(channel, msg, timeout);
    }

    public Channel getChannelById(String channelId) {
        for (Channel channel : channelGroup) {
            if (channel.id().asLongText().equals(channelId)) {
                return channel;
            }
        }
        return null;
    }

    public CompletableFuture<Object> sendToAnyChannel(Object msg, int timeout) {
        Channel channel = getAnyChannel();
        return sendToChannel(channel, msg, timeout);
    }

    public CompletableFuture<Object> sendToFirstChannel(Object msg, int timeout) {
        Channel channel = getFirstChannel();
        return sendToChannel(channel, msg, timeout);
    }

    public Channel getFirstChannel() {
        Iterator<Channel> it = channelGroup.iterator();
        if (!it.hasNext()) {
            throw new NopException(ERR_NETTY_NO_AVAILABLE_CHANNEL);
        }
        return it.next();
    }

    private CompletableFuture<Object> sendToChannel(Channel channel, Object msg, int timeout) {
        CompletableFuture<Object> ret = new CompletableFuture<>();
        IRpcMessageHandler handler = channel.pipeline().get(IRpcMessageHandler.class);
        handler.send(msg, timeout, ret);
        return ret;
    }

    public ChannelGroup getChannelGroup() {
        return channelGroup;
    }

    public Channel getAnyChannel() {
        List<Channel> channels = new ArrayList<>(channelGroup);
        if (channels.isEmpty())
            throw new NopException(ERR_NETTY_NO_AVAILABLE_CHANNEL);

        // 随机选择一个channel
        return channels.get(MathHelper.random().nextInt(channels.size()));
    }

    public List<TcpChannelInfo> getChannelInfos() {
        List<TcpChannelInfo> infos = new ArrayList<>();

        for (Channel channel : channelGroup) {
            TcpChannelInfo info = channel.attr(TcpChannelInfo.ATTR_KEY).get();
            infos.add(info);
        }
        return infos;
    }

    @Override
    protected void doStop() {
        ServerBootstrap bootstrap = this.bootstrap;
        if (bootstrap != null) {
            bootstrap.config().group().shutdownGracefully();
            if (workerGroup == null)
                bootstrap.config().childGroup().shutdownGracefully();
        }
        this.bootstrap = null;
    }
}
