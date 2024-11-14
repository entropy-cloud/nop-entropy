package io.nop.netty.ext.server;

import io.netty.channel.ChannelPipeline;
import io.nop.codec.IPacketCodec;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.fsm.execution.IStateMachine;
import io.nop.netty.ext.handlers.PacketStateMachineHandler;
import io.nop.netty.handlers.ConnectionInfoHandler;
import io.nop.netty.handlers.MessageCounterHandler;
import io.nop.netty.handlers.PacketCodecHandler;
import io.nop.netty.handlers.RpcMessageHandler;
import io.nop.netty.tcp.NettyTcpServer;
import io.nop.netty.tcp.TcpChannelInfo;
import io.nop.record.codec.FieldCodecRegistry;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.nop.netty.ext.NettyExtConstants.HANDLER_CODEC;
import static io.nop.netty.ext.NettyExtConstants.HANDLER_RPC;
import static io.nop.netty.ext.NettyExtConstants.HANDLER_STATE_MACHINE;

public class ModelBasedTcpServer extends LifeCycleSupport {
    private ModelBasedTcpServerConfig config;
    private NettyTcpServer server;

    private FieldCodecRegistry codecRegistry;

    public void setConfig(ModelBasedTcpServerConfig config) {
        this.config = config;
    }

    public void setCodecRegistry(FieldCodecRegistry codecRegistry) {
        this.codecRegistry = codecRegistry;
    }

    @Override
    protected void doStart() {
        int port = config.getPort();

        server = new NettyTcpServer();
        server.setConfig(config);
        server.setPort(port);
        server.setChannelInitializer(this::initServerChannel);
        server.start();
    }

    private void initServerChannel(ChannelPipeline pipeline) {
        IPacketCodec<Object> codec = config.loadPacketCodec(codecRegistry);
        pipeline.addLast(new ConnectionInfoHandler());
        pipeline.addLast(HANDLER_CODEC, new PacketCodecHandler<>(codec,
                config.getAggMaxMessageSize(), config.getAllowedDecodeErrorCount(), config.getAllowedEncodeErrorCount()));
        pipeline.addLast(new MessageCounterHandler());

        IStateMachine stateMachine = config.loadStateMachine();
        if (stateMachine != null) {
            pipeline.addLast(HANDLER_STATE_MACHINE, new PacketStateMachineHandler(stateMachine));
        }

        pipeline.addLast(HANDLER_RPC, new RpcMessageHandler(config.getMaxPendingTasks()));
    }

    public List<TcpChannelInfo> getChannelInfos() {
        return server.getChannelInfos();
    }

    public CompletableFuture<Object> sendToChannel(String channelId, Object msg, int timeout) {
        return server.sendToChannel(channelId, msg, timeout);
    }

    public CompletableFuture<Object> sendToAnyChannel(Object msg, int timeout) {
        return server.sendToAnyChannel(msg, timeout);
    }

    public CompletableFuture<Object> sendToFirstChannel(Object msg, int timeout) {
        return server.sendToFirstChannel(msg, timeout);
    }

    @Override
    protected void doStop() {
        if (server != null) {
            server.stop();
        }
    }
}