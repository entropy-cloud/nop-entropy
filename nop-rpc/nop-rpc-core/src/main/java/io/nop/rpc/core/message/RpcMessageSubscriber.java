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
import io.nop.api.core.message.DelegateMessageSender;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSender;
import io.nop.api.core.message.IMessageSubscriber;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.StringHelper;
import io.nop.rpc.api.IRpcService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.nop.rpc.core.RpcErrors.ARG_REQUEST;
import static io.nop.rpc.core.RpcErrors.ARG_SERVICE_METHOD;
import static io.nop.rpc.core.RpcErrors.ARG_SERVICE_NAME;
import static io.nop.rpc.core.RpcErrors.ERR_RPC_HANDLER_IS_SUSPENDED;
import static io.nop.rpc.core.RpcErrors.ERR_RPC_NO_HANDLER;

/**
 * 将RPC的服务端封装为{@link IMessageSubscriber}接口
 */
public class RpcMessageSubscriber implements IMessageSubscriber, IRpcService {
    private final String serviceName;

    // 除了reply消息之外的其他消息通过此sender发送
    private final IMessageSender sender;

    private final RpcMessageSubscriptions subscriptions = new RpcMessageSubscriptions();

    public RpcMessageSubscriber(String serviceName, IMessageSender sender) {
        this.serviceName = serviceName;
        this.sender = sender;
    }

    static class ConsumeContext extends DelegateMessageSender implements IMessageConsumeContext {
        private final ApiRequest<?> request;
        private final ICancelToken cancelToken;
        private final CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();

        public ConsumeContext(IMessageSender sender, ApiRequest<?> request, ICancelToken cancelToken) {
            super(sender);
            this.request = request;
            this.cancelToken = cancelToken;
        }

        public CompletableFuture<ApiResponse<?>> getReplyFuture() {
            return future;
        }

        @Override
        public ICancelToken getCancelToken() {
            return cancelToken;
        }

        @Override
        public void reply(Object message) {
            ApiResponse<Object> rep = null;
            if (message instanceof ApiResponse) {
                rep = (ApiResponse<Object>) message;
            } else {
                rep = ApiResponse.buildSuccess(message);
            }
            String reqId = ApiHeaders.getId(request);
            if (!StringHelper.isEmpty(reqId)) {
                ApiHeaders.setRelId(rep, reqId);
            }
            future.complete(rep);
        }
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        String topic = getTopic(serviceMethod, request);
        RpcMessageSubscriptions.Subscription subscription = subscriptions.getSubscription(topic);
        if (subscription == null) {
            return FutureHelper.reject(new NopException(ERR_RPC_NO_HANDLER).param(ARG_REQUEST, request)
                    .param(ARG_SERVICE_NAME, serviceName).param(ARG_SERVICE_METHOD, serviceMethod));
        }

        if (subscription.isSuspended()) {
            return FutureHelper.reject(new NopException(ERR_RPC_HANDLER_IS_SUSPENDED).param(ARG_REQUEST, request)
                    .param(ARG_SERVICE_NAME, serviceName).param(ARG_SERVICE_METHOD, serviceMethod));
        }

        return handleMethod(topic, subscription.getConsumer(), request, cancelToken);
    }

    private CompletionStage<ApiResponse<?>> handleMethod(String topic, IMessageConsumer consumer, ApiRequest<?> request,
                                                         ICancelToken cancelToken) {
        ConsumeContext context = new ConsumeContext(sender, request, cancelToken);

        try {
            Object ret = consumer.onMessage(topic, request, context);
            if (ret instanceof CompletionStage) {
                ((CompletionStage<?>) ret).thenAccept(v -> context.reply(v));
            } else {
                context.reply(ret);
            }
        } catch (Exception e) {
            context.getReplyFuture().completeExceptionally(e);
        }

        return context.getReplyFuture();
    }

    protected String getTopic(String serviceMethod, ApiRequest<?> request) {
        return serviceMethod;
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        return subscriptions.register(topic, listener, options);
    }
}