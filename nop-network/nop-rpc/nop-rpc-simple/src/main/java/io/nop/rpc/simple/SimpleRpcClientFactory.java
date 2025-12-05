/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.simple;

import io.nop.api.core.beans.ApiResponse;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.lang.IDestroyable;
import io.nop.commons.util.CollectionHelper;
import io.nop.rpc.api.IRpcProxy;
import io.nop.api.core.rpc.IRpcServiceInterceptor;
import io.nop.rpc.core.interceptors.LogRpcServiceInterceptor;
import io.nop.rpc.core.reflect.DefaultRpcMessageTransformer;
import io.nop.rpc.core.reflect.IRpcMessageTransformer;
import io.nop.rpc.core.reflect.RpcInvocationHandler;
import io.nop.socket.ClientConfig;
import io.nop.socket.ICommandClient;
import io.nop.socket.SocketClient;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Consumer;

public class SimpleRpcClientFactory<T> implements IDestroyable {
    private IScheduledExecutor timer;
    private IThreadPoolExecutor executor;
    private boolean ownTimer;
    private boolean ownExecutor;
    private boolean destroyed;

    private List<IRpcServiceInterceptor> interceptors;
    private IRpcMessageTransformer transformer = DefaultRpcMessageTransformer.INSTANCE;
    private ClientConfig clientConfig;
    private String serviceName;
    private Class<T> rpcInterface;
    private Consumer<ApiResponse<?>> noticeReceiver;

    private Runnable onChannelOpen;
    private Runnable onChannelClose;

    public Runnable getOnChannelOpen() {
        return onChannelOpen;
    }

    public void setOnChannelOpen(Runnable onChannelOpen) {
        this.onChannelOpen = onChannelOpen;
    }

    public Runnable getOnChannelClose() {
        return onChannelClose;
    }

    public void setOnChannelClose(Runnable onChannelClose) {
        this.onChannelClose = onChannelClose;
    }

    public IThreadPoolExecutor getExecutor() {
        return executor;
    }

    public void setExecutor(IThreadPoolExecutor executor) {
        this.executor = executor;
    }

    public List<IRpcServiceInterceptor> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<IRpcServiceInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public IRpcMessageTransformer getTransformer() {
        return transformer;
    }

    public void setTransformer(IRpcMessageTransformer transformer) {
        this.transformer = transformer;
    }

    public ClientConfig getClientConfig() {
        return clientConfig;
    }

    public void setClientConfig(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public Class<T> getRpcInterface() {
        return rpcInterface;
    }

    public void setRpcInterface(Class<T> rpcInterface) {
        this.rpcInterface = rpcInterface;
    }

    public Consumer<ApiResponse<?>> getNoticeReceiver() {
        return noticeReceiver;
    }

    public void setNoticeReceiver(Consumer<ApiResponse<?>> noticeReceiver) {
        this.noticeReceiver = noticeReceiver;
    }

    void init() {
        if (clientConfig.isLogBody()) {
            this.interceptors = CollectionHelper.prepend(interceptors, LogRpcServiceInterceptor.INSTANCE);
        }

        String clientName = clientConfig.getClientName();
        if (clientName == null)
            clientName = "default";

        if (timer == null) {
            timer = DefaultScheduledExecutor.newSingleThreadTimer("simple-rpc-client-timer-" + clientName);
            ownTimer = true;
        }
        if (executor == null) {
            executor = DefaultThreadPoolExecutor.newExecutor("simple-rpc-client-executor-" + clientName, 5, 10);
            ownExecutor = true;
        }
    }

    @Override
    public boolean isDestroyed() {
        return destroyed;
    }

    public void destroy() {
        destroyed = true;

        if (ownExecutor && executor != null) {
            executor.destroy();
        }

        if (ownTimer && timer != null) {
            timer.destroy();
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public T newInstance() {
        init();

        ICommandClient client = newClient();

        String serviceName = this.serviceName;
        if (serviceName == null)
            serviceName = rpcInterface.getName();
        SimpleRpcClient rpcService = new SimpleRpcClient(serviceName, client, noticeReceiver,
                timer);
        T obj = (T) Proxy.newProxyInstance(rpcInterface.getClassLoader(), new Class[]{rpcInterface, IRpcProxy.class},
                new RpcInvocationHandler(serviceName, rpcService, interceptors, transformer));

        client.connect();
        rpcService.executeRecv(executor);
        return obj;
    }

    protected ICommandClient newClient() {
        SocketClient client = new SocketClient();
        client.setOnChannelOpen(onChannelOpen);
        client.setOnChannelClose(onChannelClose);

        client.setTimer(timer);
        client.setClientConfig(clientConfig);
        return client;
    }
}