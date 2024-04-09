/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.core;

public interface RpcConstants {
    // 发送给服务器的请求消息
    short CMD_REQUEST = 2;

    // 从服务器接收到的响应消息
    short CMD_RESPONSE = 3;

    // 服务器主动推送到客户端的通知消息
    short CMD_NOTICE = 4;

    // 服务器处理Request失败返回的错误信息
    short CMD_ERROR = 5;

    String PROP_CANCEL_METHOD = "cancelMethod";

    String PROP_POLLING_METHOD = "pollingMethod";

    String PROP_POLL_INTERVAL = "pollInterval";

    String PROP_MAX_POLL_ERROR_COUNT = "maxPollErrorCount";

    String PROP_RESPONSE_NORMALIZER = "responseNormalizer";

    String PROP_HTTP_METHOD = "httpMethod";
    String PROP_HTTP_URL = "httpUrl";
}
