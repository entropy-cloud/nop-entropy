package io.nop.netty.ext.proxy;

import io.netty.channel.ChannelPipeline;
import io.nop.codec.IPacketCodec;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.fsm.execution.IStateMachine;
import io.nop.netty.ext.handlers.PacketStateMachineHandler;
import io.nop.netty.ext.handlers.ProxyHandler;
import io.nop.netty.ext.server.ModelBasedTcpServerConfig;
import io.nop.netty.handlers.PacketCodecHandler;
import io.nop.netty.handlers.RpcMessageHandler;
import io.nop.netty.tcp.NettyTcpServer;
import io.nop.record.codec.FieldCodecRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.netty.ext.NettyExtConstants.HANDLER_CODEC;
import static io.nop.netty.ext.NettyExtConstants.HANDLER_RPC;
import static io.nop.netty.ext.NettyExtConstants.HANDLER_STATE_MACHINE;

/**
 * 启动serverA和serverB两个服务器，实现client之间的中转调用。从serverA中接收到消息之后，会自动从serverB的连接中选择一个转发过去
 */
public class TransferServerProxy extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(ModelBasedTcpServerConfig.class);

    private FieldCodecRegistry codecRegistry = FieldCodecRegistry.DEFAULT;

    private ModelBasedTcpServerConfig serverAConfig;
    private ModelBasedTcpServerConfig serverBConfig;

    private NettyTcpServer serverA;
    private NettyTcpServer serverB;

    public void setCodecRegistry(FieldCodecRegistry codecRegistry) {
        this.codecRegistry = codecRegistry;
    }

    public void setServerAConfig(ModelBasedTcpServerConfig serverAConfig) {
        this.serverAConfig = serverAConfig;
    }

    public void setServerBConfig(ModelBasedTcpServerConfig serverBConfig) {
        this.serverBConfig = serverBConfig;
    }

    @Override
    protected void doStart() {
        int portA = serverAConfig.getPort();
        int portB = serverBConfig.getPort();

        serverA = new NettyTcpServer();
        serverA.setConfig(serverAConfig);
        serverA.setPort(portA);
        serverA.setChannelInitializer(this::initServerAChannel);

        serverB = new NettyTcpServer();
        serverB.setConfig(serverBConfig);
        serverB.setPort(portB);
        serverB.setChannelInitializer(this::initServerBChannel);

        LOG.info("nop.start-proxy:serverModePort={},clientModePort={}", portB, portA);
        serverA.start();
        serverB.start();
    }

    private void initServerAChannel(ChannelPipeline pipeline) {
        initServerChannel(pipeline, serverAConfig);
        // 从serverA接收到消息，然后作为RPC消息转发到连接serverB的客户端，当serverB返回消息时，再返回给连接serverA的客户端。
        pipeline.addLast(new ProxyHandler(serverB, serverB.getConfig().getRpcTimeout()));
    }

    private void initServerBChannel(ChannelPipeline pipeline) {
        initServerChannel(pipeline, serverBConfig);

        pipeline.addLast(HANDLER_RPC, new RpcMessageHandler(serverBConfig.getMaxPendingTasks()));
    }

    private void initServerChannel(ChannelPipeline pipeline, ModelBasedTcpServerConfig config) {
        IPacketCodec<Object> codec = config.loadPacketCodec(codecRegistry);
        pipeline.addLast(HANDLER_CODEC, new PacketCodecHandler<>(codec,
                config.getAggMaxMessageSize(), config.getAllowedDecodeErrorCount(), config.getAllowedEncodeErrorCount()));

        IStateMachine stateMachine = config.loadStateMachine();
        if (stateMachine != null) {
            pipeline.addLast(HANDLER_STATE_MACHINE, new PacketStateMachineHandler(stateMachine));
        }
    }

    @Override
    protected void doStop() {
        if (serverA != null) {
            serverA.stop();
        }

        if (serverB != null) {
            serverB.stop();
        }
    }
}
