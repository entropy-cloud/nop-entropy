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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SimpleRpcClientFactory<T> implements IDestroyable {
    static final Logger LOG = LoggerFactory.getLogger(SimpleRpcClientFactory.class);

    /**
     * 4.3-C6-010（plan 2561 裁定 K）：client 登记表——newInstance() 创建的每个
     * {@link SimpleRpcClient}（AutoCloseable）在此登记，destroy 时逆序关闭兜底，
     * 兜住调用方漏关的 client（底层 SocketClient 连接/timer 泄漏面）。
     * 幂等策略：不做 closed 守卫——SocketClient.close() 重入会再触发 onChannelClose，
     * destroy 关闭路径必须容忍重复回调。
     */
    private final List<SimpleRpcClient> clients = new ArrayList<>();

    private IScheduledExecutor timer;
    private IThreadPoolExecutor executor;
    private boolean ownTimer;
    private boolean ownExecutor;
    private boolean destroyed;
    /** C1-001（plan 2523）：init 幂等标记（volatile——newInstance 与 destroy 并发面的可见性）。 */
    private volatile boolean inited;

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
        // C1-001（plan 2523，WI 2.1 CL-024 原始站点）：init 幂等——init 挂在 newInstance 上
        // 每次调用都会执行，非幂等形态下 logBody 拦截器随调用次数无界累积、timer/executor
        // 检查-创建竞态（并发 newInstance）双建泄漏单线程定时器。Guard 兄弟形态为
        // synchronized + volatile inited 幂等早退（早退非抛错——与 destroyed 的 fail-fast
        // 两态两语义：未 init 是合法态、destroyed 后使用是编程错误）。
        synchronized (this) {
            if (inited)
                return;
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
            inited = true;
        }
    }

    @Override
    public boolean isDestroyed() {
        return destroyed;
    }

    public void destroy() {
        destroyed = true;
        inited = false;

        // 4.3-C6-010（plan 2561 裁定 K）：逆序关闭登记的 client（后建先关），先于
        // executor/timer 销毁——关闭回调（onChannelClose）仍可在存活的执行器上收尾。
        // 单个 close 失败不阻断其余 client 与 executor/timer 的销毁。
        List<SimpleRpcClient> toClose;
        synchronized (clients) {
            toClose = new ArrayList<>(clients);
            clients.clear();
        }
        for (int i = toClose.size() - 1; i >= 0; i--) {
            SimpleRpcClient client = toClose.get(i);
            try {
                client.close();
            } catch (Exception e) {
                LOG.warn("nop.rpc.simple.close-client-fail:serviceName={}", client.getServiceName(), e);
            }
        }

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
        if (destroyed) {
            // C1-001 世代令牌（plan 2523）：destroy 后使用是编程错误——在已销毁
            // executor/timer 上投递将抛未定义异常，fail-fast 显式化
            throw new IllegalStateException(
                    "SimpleRpcClientFactory is destroyed: newInstance must not be called after destroy");
        }
        init();

        ICommandClient client = newClient();

        String serviceName = this.serviceName;
        if (serviceName == null)
            serviceName = rpcInterface.getName();
        SimpleRpcClient rpcService = new SimpleRpcClient(serviceName, client, noticeReceiver,
                timer);
        // 4.3-C6-010（plan 2561 裁定 K）：登记点 = newInstance()，登记物 = SimpleRpcClient
        synchronized (clients) {
            clients.add(rpcService);
        }
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