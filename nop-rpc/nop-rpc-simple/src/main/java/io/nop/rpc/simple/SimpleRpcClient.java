/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.simple;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.lang.json.JsonTool;
import io.nop.rpc.api.IRpcService;
import io.nop.rpc.core.RpcConstants;
import io.nop.rpc.core.message.RpcChannelState;
import io.nop.socket.BinaryCommand;
import io.nop.socket.ICommandClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 使用Socket实现的简单RPC客户端。XLang的IDE调试插件用到此服务。
 */
public class SimpleRpcClient implements IRpcService, AutoCloseable {
    static final Logger LOG = LoggerFactory.getLogger(SimpleRpcClient.class);

    private final String serviceName;
    private final ICommandClient client;
    private final Consumer<ApiResponse<?>> noticeReceiver;

    private final RpcChannelState<ApiRequest<?>, ApiResponse<?>> channelState;
    private final AtomicInteger seq = new AtomicInteger();

    private Runnable onChannelOpen;
    private Runnable onChannelClose;

    public SimpleRpcClient(String serviceName, ICommandClient client,
                           Consumer<ApiResponse<?>> noticeReceiver, IScheduledExecutor timer) {
        this.serviceName = serviceName;
        this.client = client;
        this.noticeReceiver = noticeReceiver;
        channelState = new RpcChannelState<>(serviceName, timer);

        client.setOnChannelOpen(() -> {
            channelState.onChannelOpen();
            if (onChannelOpen != null)
                onChannelOpen.run();
        });

        client.setOnChannelClose(() -> {
            channelState.onChannelClose(null);
            if (onChannelClose != null)
                onChannelClose.run();
        });
    }

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

    public String getServiceName() {
        return serviceName;
    }

    public <T> void executeRecv(Executor executor) {
        client.recv(executor, cmd -> {
            String str = cmd.getDataAsString();
            ApiResponse<?> res = (ApiResponse<?>) JsonTool.parseBeanFromText(str, ApiResponse.class);
            switch (cmd.getCmd()) {
                // 服务器主动推送过来的通知消息
                case RpcConstants.CMD_NOTICE: {
                    if (noticeReceiver != null && res != null) {
                        noticeReceiver.accept(res);
                    } else {
                        LOG.info("nop.core.rpc.ignore-notice:msg={}", str);
                    }
                    break;
                }
                case RpcConstants.CMD_RESPONSE:
                case RpcConstants.CMD_ERROR: {
                    // 这里假定了单线程调用，因此发送请求与返回响应包按顺序匹配
                    String id = ApiHeaders.getRelId(res);
                    channelState.onReceive(id, res);
                }
            }
        });
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        String id = String.valueOf(seq.incrementAndGet());
        ApiHeaders.setId(request, id);

        BinaryCommand cmd;
        try {
            cmd = buildRequest(request);
        } catch (Exception e) {
            return FutureHelper.reject(e);
        }

        long timeout = client.getClientConfig().getResponseTimeout();
        CompletableFuture<ApiResponse<?>> future = channelState.prepareSend(id, request, timeout);

        try {
            client.send(cmd, true);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    BinaryCommand buildRequest(ApiRequest<?> request) {
        String str = JSON.stringify(request);
        return client.newCommand(RpcConstants.CMD_REQUEST, (short) 0, str);
    }
}