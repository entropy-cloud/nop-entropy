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
import io.nop.netty.tcp.NettyTcpClient;
import io.nop.record.codec.FieldCodecRegistry;

import java.util.concurrent.CompletableFuture;

import static io.nop.netty.ext.NettyExtConstants.HANDLER_CODEC;
import static io.nop.netty.ext.NettyExtConstants.HANDLER_RPC;
import static io.nop.netty.ext.NettyExtConstants.HANDLER_STATE_MACHINE;

public class ModelBasedTcpClient extends LifeCycleSupport {
    private ModelBasedTcpClientConfig config;
    private NettyTcpClient client;

    private FieldCodecRegistry codecRegistry = FieldCodecRegistry.DEFAULT;

    public void setConfig(ModelBasedTcpClientConfig config) {
        this.config = config;
    }

    public ModelBasedTcpClientConfig getConfig() {
        return config;
    }

    public void setCodecRegistry(FieldCodecRegistry codecRegistry) {
        this.codecRegistry = codecRegistry;
    }

    @Override
    protected void doStart() {
        client = new NettyTcpClient();
        client.setConfig(config);
        client.setChannelInitializer(this::initClientChannel);
        client.start();
    }

    private void initClientChannel(ChannelPipeline pipeline) {
        IPacketCodec<Object> codec = config.loadPacketCodec(codecRegistry);
        pipeline.addLast(new ConnectionInfoHandler());
        pipeline.addLast(HANDLER_CODEC, new PacketCodecHandler(codec,
                config.getAggMaxMessageSize(), config.getAllowedDecodeErrorCount(), config.getAllowedEncodeErrorCount()));
        pipeline.addLast(new MessageCounterHandler());

        IStateMachine stateMachine = config.loadStateMachine();
        if (stateMachine != null) {
            pipeline.addLast(HANDLER_STATE_MACHINE, new PacketStateMachineHandler(stateMachine));
        }

        pipeline.addLast(HANDLER_RPC, new RpcMessageHandler(config.getMaxPendingTasks()));
    }

    public CompletableFuture<Object> sendAsync(Object msg, int timeout) {
        return client.sendAsync(msg, timeout);
    }

    public Object send(Object msg, int timeout) {
        return client.send(msg, timeout);
    }

    @Override
    protected void doStop() {
        if (client != null) {
            client.stop();
        }
    }
}