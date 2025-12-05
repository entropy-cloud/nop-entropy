/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.simple;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.service.ILifeCycle;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.rpc.api.AopRpcService;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.rpc.IRpcServiceInterceptor;
import io.nop.rpc.core.RpcConstants;
import io.nop.rpc.core.interceptors.LogRpcServiceInterceptor;
import io.nop.rpc.core.reflect.DefaultRpcMessageTransformer;
import io.nop.rpc.core.reflect.IRpcMessageTransformer;
import io.nop.rpc.core.reflect.MultiRpcService;
import io.nop.rpc.core.reflect.ReflectiveRpcService;
import io.nop.socket.BinaryCommand;
import io.nop.socket.ICommandServer;
import io.nop.socket.ServerConfig;
import io.nop.socket.SocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static io.nop.rpc.core.RpcErrors.ERR_RPC_EMPTY_REQUEST;

/**
 * 使用Socket实现的简单RPC服务器。XLang的IDE调试插件用到此服务。
 */
public class SimpleRpcServer implements ILifeCycle {
    static final Logger LOG = LoggerFactory.getLogger(SimpleRpcServer.class);

    private ServerConfig serverConfig;
    private IThreadPoolExecutor executor;
    private boolean ownExecutor;
    private IRpcService handler;
    private ICommandServer socketServer;

    private List<IRpcServiceInterceptor> interceptors;
    private IRpcMessageTransformer transformer = DefaultRpcMessageTransformer.INSTANCE;
    private Map<Class<?>, Object> serviceImpls;

    private Consumer<String> onChannelOpen;
    private Consumer<String> onChannelClose;

    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    public void setServerConfig(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
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

    public void setServiceImpls(Map<Class<?>, Object> serviceImpls) {
        this.serviceImpls = serviceImpls;
    }

    public void addServiceImpl(Class<?> clazz, Object impl) {
        if (this.serviceImpls == null)
            this.serviceImpls = new HashMap<>();
        serviceImpls.put(clazz, impl);
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

    protected void init() {
        if (serverConfig.isLogBody()) {
            this.interceptors = CollectionHelper.prepend(interceptors, LogRpcServiceInterceptor.INSTANCE);
        }

        if (executor == null) {
            String serverName = serverConfig.getServerName();
            if (serverName == null)
                serverName = "default";
            executor = DefaultThreadPoolExecutor.newExecutor("simple-rpc-server-" + serverName,
                    serverConfig.getThreadPoolSize(), 10);
            ownExecutor = true;
        }
    }

    public boolean isActive() {
        return socketServer.isActive();
    }

    @Override
    public void start() {
        Guard.checkState(socketServer == null, "server already inited");

        init();

        this.socketServer = newServer();
        this.handler = buildHandler();

        this.socketServer.start();
    }

    protected ICommandServer newServer() {
        SocketServer server = new SocketServer();
        server.setServerConfig(serverConfig);
        server.setExecutor(executor);
        server.setCommandHandler(this::handleCommandSync);
        server.setOnChannelOpen(onChannelOpen);
        server.setOnChannelClose(onChannelClose);
        return server;
    }

    public boolean waitConnected(long timeout) {
        return socketServer.waitConnected(timeout);
    }

    @Override
    public void stop() {
        if (socketServer != null) {
            socketServer.stop();
        }

        if (ownExecutor) {
            if (executor != null)
                executor.destroy();
        }
    }

    public void broadcast(short cmd, short flags, ApiResponse<?> res) {
        BinaryCommand command = socketServer.newCommand(cmd, flags, JSON.stringify(res));
        socketServer.broadcast(command);
    }

    public void broadcastNotice(ApiResponse<?> res) {
        broadcast(RpcConstants.CMD_NOTICE, (short) 0, res);
    }

    public void sendTo(String addr, short cmd, short flags, ApiResponse<?> res) {
        BinaryCommand command = socketServer.newCommand(cmd, flags, JSON.stringify(res));
        socketServer.sendTo(addr, command);
    }

    public void sendNoticeTo(String addr, ApiResponse<?> res) {
        sendTo(addr, RpcConstants.CMD_NOTICE, (short) 0, res);
    }

    private IRpcService buildHandler() {
        Map<String, IRpcService> services = new HashMap<>(serviceImpls.size());
        for (Map.Entry<Class<?>, Object> entry : serviceImpls.entrySet()) {
            Class<?> clazz = entry.getKey();
            Object impl = entry.getValue();

            String serviceName = clazz.getName();
            IRpcService service = new ReflectiveRpcService(serviceName, clazz, impl, transformer);
            if (interceptors != null && !interceptors.isEmpty()) {
                service = new AopRpcService(serviceName, service, interceptors, true);
            }
            services.put(serviceName, service);
        }

        return new MultiRpcService(services);
    }

    protected BinaryCommand handleCommandSync(String addr, BinaryCommand request) {
        ApiRequest<Object> req = null;
        ApiResponse<?> res;
        try {
            String str = request.getDataAsString();
            if (StringHelper.isEmpty(str)) {
                throw new NopException(ERR_RPC_EMPTY_REQUEST);
            }
            req = (ApiRequest<Object>) JsonTool.parseBeanFromText(str, ApiRequest.class);
            ApiHeaders.setClientAddr(req, addr);
            res = handler.call(ApiHeaders.getSvcAction(req), req, null);
        } catch (Exception e) {
            LOG.error("nop.err.socket.handle-command-fail", e);
            res = ErrorMessageManager.instance().buildResponse(req, e);
        }

        transformer.enrichResponse(req, res);
        String result = JSON.stringify(res);
        return socketServer.newCommand(res.isOk() ? RpcConstants.CMD_RESPONSE : RpcConstants.CMD_ERROR, (short) 0,
                result);
    }
}