/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.core.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageSender;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ApiInvokeHelper;
import io.nop.commons.util.StringHelper;
import io.nop.rpc.api.IRpcService;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.rpc.core.RpcErrors.ARG_ALLOWED_TOPICS;
import static io.nop.rpc.core.RpcErrors.ARG_TOPIC;
import static io.nop.rpc.core.RpcErrors.ERR_RPC_INVALID_MESSAGE_TOPIC;

/**
 * 将RPC接口包装为消息发送接口。另一方面，可以通过{@link MessageRpcClient}将消息发送和接收包装为RPC接口。
 */
public class RpcMessageSender implements IMessageSender {
    private final Map<String, IRpcService> rpcServices;
    private final IRpcService defaultService;

    public RpcMessageSender(Map<String, IRpcService> rpcServices, IRpcService defaultService) {
        this.rpcServices = rpcServices == null ? Collections.emptyMap() : rpcServices;
        this.defaultService = defaultService;
    }

    public RpcMessageSender(Map<String, IRpcService> rpcServices) {
        this(rpcServices, null);
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        ApiRequest<?> request = (ApiRequest<?>) message;
        IRpcService rpcService = getRpcService(topic);

        String action = ApiHeaders.getSvcAction(request);
        if (StringHelper.isEmpty(action)) {
            action = topic;
        }
        return rpcService.callAsync(action, request, options == null ? null : options.getCancelToken())
                .thenApply(ApiInvokeHelper::ignoreResult);
    }

    private IRpcService getRpcService(String topic) {
        IRpcService rpcService = rpcServices.get(topic);
        if (rpcService == null)
            rpcService = defaultService;
        if (rpcService == null)
            throw new NopException(ERR_RPC_INVALID_MESSAGE_TOPIC).param(ARG_TOPIC, topic).param(ARG_ALLOWED_TOPICS,
                    rpcServices.keySet());
        return rpcService;
    }
}