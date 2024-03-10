/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.core;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface RpcErrors {
    String ARG_SERVICE_NAME = "serviceName";
    String ARG_CLASS_NAME = "className";

    String ARG_REQUEST = "request";
    String ARG_RESPONSE = "response";
    String ARG_RPC_CHANNEL = "rpcChannel";

    String ARG_MAX_WAIT_REQUESTS = "maxWaitRequests";
    String ARG_SERVICE_METHOD = "serviceMethod";

    String ARG_COUNT = "count";
    String ARG_EXPECTED_COUNT = "expectedCount";

    String ARG_REQ_ID = "reqId";

    String ARG_TOPIC = "topic";
    String ARG_ALLOWED_TOPICS = "allowedTopics";

    String ARG_CANCEL_REASON = "cancelReason";

    ErrorCode ERR_RPC_CHANNEL_CLOSED = define("nop.err.rpc.channel-closed", "RPC连接已关闭");

    ErrorCode ERR_RPC_TOO_MANY_INFLIGHT_MESSAGES = define("nop.err.rpc.too-many-inflight-messages",
            "正在等待响应的RPC消息过多，超过最大限制[{maxInflights]", ARG_MAX_WAIT_REQUESTS);

    ErrorCode ERR_RPC_NO_HANDLER = define("nop.err.rpc.no-handler", "RPC服务没有找到合适的处理函数", ARG_REQUEST, ARG_SERVICE_NAME,
            ARG_SERVICE_METHOD);

    ErrorCode ERR_RPC_NOT_ALLOW_METHOD_OVERLOAD = define("nop.err.rpc.not-allow-method-overload",
            "服务[{serviceName}]的方法[{serviceMethod}]不支持函数重载", ARG_SERVICE_NAME, ARG_SERVICE_METHOD);

    ErrorCode ERR_RPC_REQUEST_BODY_NOT_LIST = define("nop.err.rpc.request-body-not-list", "请求对象的数据内容必须是列表类型");

    ErrorCode ERR_RPC_REQUEST_ARGS_COUNT_MISMATCH = define("nop.err.rpc.request-args-count-mismatch",
            "服务方法[{serviceMethod}]的请求参数个数不匹配", ARG_SERVICE_METHOD);

    ErrorCode ERR_RPC_EMPTY_REQUEST = define("nop.err.rpc.empty-request", "请求对象为空");

    ErrorCode ERR_RPC_MISSING_SERVICE_HEADER = define("nop.err.rpc.missing-service-header", "缺少nop-service参数");

    ErrorCode ERR_RPC_UNKNOWN_SERVICE = define("nop.err.rpc.unknown-service", "未定义的服务:{serviceName}", ARG_SERVICE_NAME);

    ErrorCode ERR_RPC_TIMEOUT_EXCEPTION = define("nop.err.rpc.timeout", "RPC调用超时");

    ErrorCode ERR_RPC_INVALID_MESSAGE_TOPIC = define("nop.err.rpc.invalid-message-topic", "非法的RPC消息主题：{topic}",
            ARG_TOPIC, ARG_ALLOWED_TOPICS);

    ErrorCode ERR_RPC_NOT_ALLOW_MULTIPLE_SUBSCRIPTION = define("nop.err.rpc.not-allow-multiple-subscription",
            "不允许多次订阅消息主题: {topic}", ARG_TOPIC);

    ErrorCode ERR_RPC_HANDLER_IS_SUSPENDED = define("nop.err.rpc.handler-is-suspended", "消息处理处于暂停状态",
            ARG_SERVICE_METHOD);

    ErrorCode ERR_RPC_CANCELLED = define("nop.err.rpc.cancelled", "RPC调用已取消，原因:{reason}", ARG_CANCEL_REASON);
}
