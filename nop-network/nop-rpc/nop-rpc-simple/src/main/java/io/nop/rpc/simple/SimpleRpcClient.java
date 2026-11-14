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
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.lang.json.JsonTool;
import io.nop.api.core.rpc.IRpcService;
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

    // 4.3-C1-005（plan 2560 裁定 K）：volatile——晚设置回调对连接事件线程可见
    private volatile Runnable onChannelOpen;
    private volatile Runnable onChannelClose;

    // plan 2572 Phase 2：close 时回调 factory 摘除登记——长寿 factory 反复 newInstance 时
    // 登记表不因正常关闭累积（兜底表自身变泄漏源的半边缺口）。volatile 保证 close 与 destroy 竞态可见。
    private volatile Runnable onCloseCallback;

    public SimpleRpcClient(String serviceName, ICommandClient client,
                           Consumer<ApiResponse<?>> noticeReceiver, IScheduledExecutor timer) {
        this.serviceName = serviceName;
        this.client = client;
        this.noticeReceiver = noticeReceiver;
        channelState = new RpcChannelState<>(serviceName, timer);

        // 4.3-C1-005（plan 2560 裁定 K）：工厂先行配置的回调不再被构造器覆写静默丢弃——
        // 捕获前值合并（工厂回调先行，内部 channelState 回调后行）
        Runnable prevOpen = client.getOnChannelOpen();
        Runnable prevClose = client.getOnChannelClose();
        client.setOnChannelOpen(() -> {
            if (prevOpen != null)
                prevOpen.run();
            channelState.onChannelOpen();
            if (onChannelOpen != null)
                onChannelOpen.run();
        });

        client.setOnChannelClose(() -> {
            if (prevClose != null)
                prevClose.run();
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
        try {
            client.close();
        } finally {
            Runnable cb = onCloseCallback;
            if (cb != null)
                cb.run();
        }
    }

    /** plan 2572 Phase 2：factory 注入的登记摘除回调（close 时调用，须幂等）。 */
    public void setOnCloseCallback(Runnable onCloseCallback) {
        this.onCloseCallback = onCloseCallback;
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
            // 4.3-C3-009（plan 2561 裁定 E）：send 失败必须经 channelState.onFailure 完成
            // waitFutures 中登记的 raw future（remove + completeExceptionally，对齐
            // MessageRpcClient 先例）。原形态对 prepareSend 返回的 whenComplete 派生 future
            // 直接 completeExceptionally，raw future 无人完成 → waitFutures 泄漏（只能等超时清理）。
            channelState.onFailure(id, e);
        }
        return future;
    }

    BinaryCommand buildRequest(ApiRequest<?> request) {
        String str = JSON.stringify(request);
        return client.newCommand(RpcConstants.CMD_REQUEST, (short) 0, str);
    }
}