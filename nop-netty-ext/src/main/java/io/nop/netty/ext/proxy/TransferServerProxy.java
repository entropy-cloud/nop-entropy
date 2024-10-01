package io.nop.netty.ext.proxy;

import io.netty.channel.ChannelPipeline;
import io.nop.codec.IPacketCodec;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.fsm.execution.IStateMachine;
import io.nop.netty.ext.handlers.PacketStateMachineHandler;
import io.nop.netty.ext.handlers.ProxyHandler;
import io.nop.netty.handlers.PacketCodecHandler;
import io.nop.netty.handlers.RpcMessageHandler;
import io.nop.netty.tcp.NettyTcpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 启动serverA和serverB两个服务器，实现client之间的中转调用。从serverA中接收到消息之后，会自动从serverB的连接中选择一个转发过去
 */
public class TransferServerProxy extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(TransferServerProxyConfig.class);

    static final String CODEC_NAME = "codec";
    static final String STATE_MACHINE_NAME = "stateMachine";
    static final String RPC_NAME = "rpc";

    private TransferServerProxyConfig serverAConfig;
    private TransferServerProxyConfig serverBConfig;

    private NettyTcpServer serverA;
    private NettyTcpServer serverB;

    public void setServerAConfig(TransferServerProxyConfig serverAConfig) {
        this.serverAConfig = serverAConfig;
    }

    public void setServerBConfig(TransferServerProxyConfig serverBConfig) {
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
        pipeline.addLast(new ProxyHandler(serverB));
    }

    private void initServerBChannel(ChannelPipeline pipeline) {
        initServerChannel(pipeline, serverBConfig);

        pipeline.addLast(RPC_NAME, new RpcMessageHandler(serverBConfig.getMaxPendingTasks()));
    }

    private void initServerChannel(ChannelPipeline pipeline, TransferServerProxyConfig config) {
        IPacketCodec codec = config.loadPacketCodec();
        pipeline.addLast(CODEC_NAME, new PacketCodecHandler(codec,
                config.getAggMaxMessageSize(), config.getAllowedDecodeErrorCount(), config.getAllowedEncodeErrorCount()));

        IStateMachine stateMachine = config.loadStateMachine();
        if (stateMachine != null) {
            pipeline.addLast(STATE_MACHINE_NAME, new PacketStateMachineHandler(stateMachine));
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
