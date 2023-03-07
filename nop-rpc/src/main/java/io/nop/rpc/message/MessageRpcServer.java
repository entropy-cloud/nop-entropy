/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.rpc.IRpcMessageAdapter;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.service.LifeCycleSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

import static io.nop.api.core.util.Guard.notEmpty;
import static io.nop.api.core.util.Guard.notNull;

/**
 * 基于消息队列实现的RPC调用服务端
 */
public class MessageRpcServer extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(MessageRpcServer.class);

    private String topic;
    private IMessageService messageService;
    private IRpcService rpcHandler;
    private IRpcMessageAdapter<ApiRequest<?>, ApiResponse<?>> messageAdapter;

    private IMessageSubscription subscription;

    public IMessageService getMessageService() {
        return messageService;
    }

    public void setMessageService(IMessageService messageService) {
        this.messageService = messageService;
    }

    public void setRpcService(IRpcService rpcHandler) {
        this.rpcHandler = rpcHandler;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setMessageAdapter(IRpcMessageAdapter<ApiRequest<?>, ApiResponse<?>> messageAdapter) {
        this.messageAdapter = messageAdapter;
    }

    @Override
    protected void doStart() {
        notEmpty(topic, "topic is null");
        notNull(rpcHandler, "rpcHandler is null");
        notNull(messageService, "messageService is null");

        subscription = messageService.subscribe(topic, new IMessageConsumer() {
            @Override
            public CompletionStage<ApiResponse<?>> onMessage(String topic, Object data,
                                                             IMessageConsumeContext context) {
                Object reqId = messageAdapter.getMessageId((ApiRequest<?>) data);
                LOG.debug("nop.async.rpc.receive-message:topic={},reqId={},request={}", topic, reqId, data);

                return processRequest(reqId, (ApiRequest<?>) data, context);
            }
        });
    }

    CompletionStage<ApiResponse<?>> processRequest(Object reqId, ApiRequest<?> request,
                                                   IMessageConsumeContext context) {
        try {
            String action = messageAdapter.getRpcAction(request);
            CompletionStage<ApiResponse<?>> promise = rpcHandler.callAsync(action, request, null);
            if (promise != null) {
                return promise.exceptionally(ex -> {
                    LOG.info("nop.async.rpc.process-return-exception:topic={},reqId={}", topic, reqId, ex);
                    ApiResponse<?> response = messageAdapter.getErrorResponse(ex, request);
                    return response;
                });
            } else {
                LOG.debug("nop.async.rpc.process-request-return-null:reqId={},request={}", reqId, request);
                return null;
            }
        } catch (Throwable e) {
            LOG.error("nop.async.rpc.process-request-fail:topic={},reqId={}", topic, reqId, e);
            ApiResponse<?> response = messageAdapter.getErrorResponse(e, request);
            return FutureHelper.success(response);
        }
    }

    @Override
    protected void doStop() {
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
    }
}