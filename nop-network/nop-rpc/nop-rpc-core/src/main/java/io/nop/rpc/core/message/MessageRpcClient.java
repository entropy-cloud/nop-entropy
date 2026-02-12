/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.core.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.rpc.api.IRpcMessageAdapter;
import io.nop.api.core.rpc.IRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

import static io.nop.api.core.util.Guard.notEmpty;
import static io.nop.api.core.util.Guard.notNull;
import static io.nop.rpc.core.RpcErrors.ARG_CANCEL_REASON;
import static io.nop.rpc.core.RpcErrors.ERR_RPC_CANCELLED;

/**
 * 基于消息队列实现的RPC调用客户端。
 */
public class MessageRpcClient extends LifeCycleSupport implements IRpcService {
    static final Logger LOG = LoggerFactory.getLogger(MessageRpcClient.class);

    private IRpcMessageAdapter messageAdapter;
    private IMessageService messageService;
    private String topic;
    private IRpcChannelState channelState;

    private IMessageSubscription subscription;

    public void setMessageAdapter(IRpcMessageAdapter messageAdapter) {
        this.messageAdapter = messageAdapter;
    }

    public void setMessageService(IMessageService messageService) {
        this.messageService = messageService;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setChannelState(IRpcChannelState channelState) {
        this.channelState = channelState;
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        messageAdapter.setRpcAction(request, serviceMethod);

        boolean oneWay = messageAdapter.isOneWay(request);
        Object id = messageAdapter.getMessageId(request);
        long timeout = messageAdapter.getTimeout(request);

        MessageSendOptions options = null;
        if (timeout > 0) {
            options = new MessageSendOptions();
            options.setSendTimeout(timeout);
        }

        if (cancelToken != null) {
            if (cancelToken.isCancelled())
                return FutureHelper.reject(
                        new NopException(ERR_RPC_CANCELLED).param(ARG_CANCEL_REASON, cancelToken.getCancelReason()));

            if (options == null)
                options = new MessageSendOptions();
            options.setCancelToken(cancelToken);
        }

        String topic = getTopic(request);

        if (oneWay) {
            return messageService.sendAsync(topic, request, options).thenApply(r -> ApiResponse.success(null));
        } else {
            CompletionStage<ApiResponse<?>> promise = (CompletionStage<ApiResponse<?>>) channelState.prepareSend(id,
                    request, timeout);

            if (cancelToken != null) {
                Cancellable cancellable = new Cancellable();
                cancelToken.appendOnCancel(cancellable::cancel);

                promise.exceptionally(err -> {
                    if (FutureHelper.isTimeoutException(err)) {
                        cancellable.cancel(ICancellable.CANCEL_REASON_TIMEOUT);
                    }
                    return null;
                });
            }
            messageService.sendAsync(topic, request, options);
            return promise;
        }
    }

    protected String getTopic(ApiRequest<?> request) {
        return topic;
    }

    @Override
    protected void doStart() {
        notEmpty(topic, "topic is null");
        notNull(messageService, "messageService is null");

        String replyTopic = messageService.getAckTopic(topic);
        subscription = messageService.subscribe(replyTopic, new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object data, IMessageConsumeContext context) {
                ApiResponse<?> res = (ApiResponse<?>) data;
                Object reqId = messageAdapter.getCorrelationId(res);
                LOG.debug("nop.async.rpc.receive-response:topic={},reqId={},response={}", topic, reqId, data);

                channelState.onReceive(reqId, res);
                return null;
            }
        });
    }

    @Override
    protected void doStop() {
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
    }
}
