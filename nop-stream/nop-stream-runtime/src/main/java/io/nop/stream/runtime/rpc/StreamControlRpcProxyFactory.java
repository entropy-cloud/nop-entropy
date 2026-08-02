/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.message.IMessageService;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.rpc.core.message.DefaultRpcMessageAdapter;
import io.nop.rpc.core.message.MessageRpcClient;
import io.nop.rpc.core.message.RpcChannelState;
import io.nop.rpc.core.reflect.RpcServiceProxyFactoryBean;

/**
 * Stage 39 Phase 2: client-side factory that builds a remote RPC proxy for a
 * streaming control-plane service ({@link IStreamTaskRpcService} on the
 * coordinator side, {@link IStreamCoordinatorRpcService} on the task side).
 *
 * <p>Wires {@link MessageRpcClient} (RPC-over-{@link IMessageService}, request-response
 * over the message service reply topic) with a {@link RpcChannelState} for
 * request/response correlation and timeout, and a {@link RpcServiceProxyFactoryBean}
 * that produces a dynamic proxy implementing the service interface. The proxy
 * serializes each call into an {@code ApiRequest} (via
 * {@link StreamControlRpcTransformer}) and dispatches it through the
 * {@code MessageRpcClient}.
 *
 * <p>The returned proxy is a genuine RPC boundary, not a direct Java reference —
 * calls traverse the {@link IMessageService} transport to the
 * {@link StreamControlRpcServer} on the peer. This is the basis of the Phase 2
 * wiring-verification contract (plan guide #23): in distributed form the
 * coordinator's {@code IStreamTaskRpcService} is an RPC proxy, not a
 * {@code TaskManager} reference.
 */
@Internal
public class StreamControlRpcProxyFactory extends LifeCycleSupport {

    private final String serviceName;
    private final Class<?> serviceInterface;
    private final IMessageService messageService;
    private final String topic;

    private final MessageRpcClient client;
    private final IScheduledExecutor timer;
    private final RpcChannelState<Object, Object> channelState;
    private final Object proxy;

    public StreamControlRpcProxyFactory(String serviceName,
                                        Class<?> serviceInterface,
                                        IMessageService messageService,
                                        String topic) {
        this.serviceName = serviceName;
        this.serviceInterface = serviceInterface;
        this.messageService = messageService;
        this.topic = topic;

        this.timer = DefaultScheduledExecutor.newSingleThreadTimer("stream-rpc-client-" + topic);
        this.channelState = new RpcChannelState<>("stream-rpc:" + topic, timer);

        MessageRpcClient cli = new MessageRpcClient();
        cli.setTopic(topic);
        cli.setMessageService(messageService);
        cli.setMessageAdapter(DefaultRpcMessageAdapter.INSTANCE);
        cli.setChannelState(channelState);
        this.client = cli;

        RpcServiceProxyFactoryBean factoryBean = new RpcServiceProxyFactoryBean();
        factoryBean.setServiceName(serviceName);
        factoryBean.setServiceClass(serviceInterface);
        factoryBean.setRpcService(cli);
        factoryBean.setMessageTransformer(StreamControlRpcTransformer.INSTANCE);
        factoryBean.init();
        this.proxy = factoryBean.getObject();
    }

    /**
     * Returns the RPC proxy implementing the service interface. Calls on this object
     * traverse the {@link IMessageService} transport; it is NOT a direct reference to
     * the peer implementation.
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy() {
        return (T) proxy;
    }

    public String getTopic() {
        return topic;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Class<?> getServiceInterface() {
        return serviceInterface;
    }

    @Override
    protected void doStart() {
        channelState.onChannelOpen();
        client.start();
    }

    @Override
    protected void doStop() {
        try {
            client.stop();
        } finally {
            channelState.onChannelClose(null);
            timer.destroy();
        }
    }
}
